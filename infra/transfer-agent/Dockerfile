FROM golang:1.23-alpine AS builder
WORKDIR /app
COPY go.mod main.go ./
RUN CGO_ENABLED=0 GOOS=linux go build -o transfer-agent .

FROM rclone/rclone:latest
COPY --from=builder /app/transfer-agent /usr/local/bin/transfer-agent
ENTRYPOINT ["/usr/local/bin/transfer-agent"]
