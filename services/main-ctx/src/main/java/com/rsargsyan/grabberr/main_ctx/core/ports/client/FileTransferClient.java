package com.rsargsyan.grabberr.main_ctx.core.ports.client;

public interface FileTransferClient {
  void startTransfer(String sourcePath, String s3Key);
}
