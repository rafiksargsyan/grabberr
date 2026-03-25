package com.rsargsyan.grabberr.main_ctx.core.ports.client;

public interface ObjectStorageClient {
  void upload(String path, byte[] bytes);
  String generateSignedUrl(String path);
}
