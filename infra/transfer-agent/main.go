package main

import (
	"encoding/json"
	"log"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
)

type TransferRequest struct {
	SourcePath string `json:"sourcePath"`
	S3Key      string `json:"s3Key"`
}

var bucket string

func main() {
	bucket = os.Getenv("TRANSFER_AGENT_BUCKET")
	if bucket == "" {
		log.Fatal("TRANSFER_AGENT_BUCKET env var required")
	}

	http.HandleFunc("/transfer", handleTransfer)
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

	go func() {
		cmd := exec.Command("rclone", "copyto", src, dst, "--log-level", "INFO")
		cmd.Stdout = os.Stdout
		cmd.Stderr = os.Stderr
		if err := cmd.Run(); err != nil {
			log.Printf("rclone failed: %s -> %s: %v", src, dst, err)
		} else {
			log.Printf("rclone completed: %s -> %s", src, dst)
		}
	}()

	w.WriteHeader(http.StatusAccepted)
}
