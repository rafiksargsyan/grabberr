package com.rsargsyan.grabberr.main_ctx.core.ports.client;

public interface FileServerClient {
  byte[] download(String relativePath);
}
