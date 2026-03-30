package main

import (
	"bufio"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"strconv"
	"strings"
	"sync"
)

type TransferRequest struct {
	SourcePath string `json:"sourcePath"`
	S3Key      string `json:"s3Key"`
}

type transferState struct {
	Status   string  `json:"status"`
	Progress float64 `json:"progress"` // 0.0 to 1.0
}

const (
	statusRunning = "RUNNING"
	statusDone    = "DONE"
	statusFailed  = "FAILED"
)

var (
	bucket            string
	transfers         sync.Map // key: sourcePath, value: transferState
	uploadConcurrency string
	chunkSize         string
	bufferSize        string
	statsInterval     string
)

func main() {
	bucket = os.Getenv("TRANSFER_AGENT_BUCKET")
	if bucket == "" {
		log.Fatal("TRANSFER_AGENT_BUCKET env var required")
	}

	uploadConcurrency = getEnv("RCLONE_UPLOAD_CONCURRENCY", "8")
	chunkSize = getEnv("RCLONE_CHUNK_SIZE", "64M")
	bufferSize = getEnv("RCLONE_BUFFER_SIZE", "64M")
	statsInterval = getEnv("RCLONE_STATS_INTERVAL", "30s")

	http.HandleFunc("/transfer", handleTransfer)
	http.HandleFunc("/transfers/status", handleTransferStatus)
	log.Println("transfer-agent listening on :8080")
	log.Fatal(http.ListenAndServe(":8080", nil))
}

func handleTransfer(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}

	var req TransferRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "invalid request body", http.StatusBadRequest)
		return
	}
	if req.SourcePath == "" || req.S3Key == "" {
		http.Error(w, "sourcePath and s3Key are required", http.StatusBadRequest)
		return
	}

	src := filepath.Join("/downloads", filepath.Clean(req.SourcePath))
	if !strings.HasPrefix(src, "/downloads/") {
		http.Error(w, "invalid source path", http.StatusBadRequest)
		return
	}
	dst := "s3:" + bucket + "/" + req.S3Key

	transfers.Store(req.SourcePath, transferState{Status: statusRunning, Progress: 0})

	go func() {
		cmd := exec.Command("rclone", "copyto", src, dst,
			"--no-traverse",
			"--s3-upload-concurrency", uploadConcurrency,
			"--s3-chunk-size", chunkSize,
			"--buffer-size", bufferSize,
			"--use-json-log",
			"--stats", statsInterval,
			"--log-level", "INFO")

		cmd.Stdout = os.Stdout

		stderr, err := cmd.StderrPipe()
		if err != nil {
			log.Printf("failed to get stderr pipe for %s: %v", src, err)
			transfers.Store(req.SourcePath, transferState{Status: statusFailed})
			return
		}

		if err := cmd.Start(); err != nil {
			log.Printf("failed to start rclone for %s: %v", src, err)
			transfers.Store(req.SourcePath, transferState{Status: statusFailed})
			return
		}

		scanner := bufio.NewScanner(stderr)
		for scanner.Scan() {
			line := scanner.Text()
			fmt.Fprintln(os.Stderr, line)
			if pct, ok := parseProgress(line); ok {
				transfers.Store(req.SourcePath, transferState{Status: statusRunning, Progress: pct})
			}
		}

		if err := cmd.Wait(); err != nil {
			log.Printf("rclone failed: %s -> %s: %v", src, dst, err)
			transfers.Store(req.SourcePath, transferState{Status: statusFailed, Progress: 0})
		} else {
			log.Printf("rclone completed: %s -> %s", src, dst)
			transfers.Store(req.SourcePath, transferState{Status: statusDone, Progress: 1})
		}
	}()

	w.WriteHeader(http.StatusAccepted)
}

// parseProgress extracts a 0.0–1.0 progress value from a rclone JSON log line.
// rclone stats lines look like: {"msg":"Transferred: 1.23 GiB / 10.00 GiB, 12%, ..."}
func parseProgress(line string) (float64, bool) {
	var entry struct {
		Msg string `json:"msg"`
	}
	if err := json.Unmarshal([]byte(line), &entry); err != nil {
		return 0, false
	}
	idx := strings.Index(entry.Msg, "%")
	if idx <= 0 {
		return 0, false
	}
	start := idx - 1
	for start > 0 && entry.Msg[start-1] >= '0' && entry.Msg[start-1] <= '9' {
		start--
	}
	pct, err := strconv.Atoi(entry.Msg[start:idx])
	if err != nil {
		return 0, false
	}
	return float64(pct) / 100.0, true
}

func getEnv(key, defaultVal string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return defaultVal
}

func handleTransferStatus(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}

	path := r.URL.Query().Get("path")
	if path == "" {
		http.Error(w, "path query param required", http.StatusBadRequest)
		return
	}

	val, ok := transfers.Load(path)
	if !ok {
		http.Error(w, "unknown transfer", http.StatusNotFound)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(val.(transferState))
}
