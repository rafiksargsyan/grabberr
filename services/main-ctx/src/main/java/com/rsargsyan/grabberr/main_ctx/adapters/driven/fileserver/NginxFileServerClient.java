package com.rsargsyan.grabberr.main_ctx.adapters.driven.fileserver;

import com.rsargsyan.grabberr.main_ctx.core.ports.client.FileServerClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class NginxFileServerClient implements FileServerClient {

  @Value("${grabberr.file-server.base-url}")
  private String baseUrl;

  @Override
  public byte[] download(String relativePath) {
    // TODO: GET {baseUrl}/{relativePath} → response body bytes
    throw new UnsupportedOperationException("Not yet implemented");
  }
}
