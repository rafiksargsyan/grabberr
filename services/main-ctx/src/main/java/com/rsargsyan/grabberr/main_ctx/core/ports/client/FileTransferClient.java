package com.rsargsyan.grabberr.main_ctx.core.ports.client;

public interface FileTransferClient {

  enum TransferStatus { RUNNING, DONE, FAILED, UNKNOWN }

  record TransferStatusResult(TransferStatus status, Float progress) {}

  void startTransfer(String sourcePath, String s3Key);

  TransferStatusResult getTransferStatus(String sourcePath);

  void cancelTransfer(String sourcePath);
}
