package com.rsargsyan.grabberr.main_ctx.core.ports.client;

import java.util.Optional;

public interface ObjectStorageClient {
  void upload(String key, byte[] bytes, String contentType);
  byte[] download(String key);
  Optional<Long> getSize(String path);
  String generateSignedUrl(String path);
}
