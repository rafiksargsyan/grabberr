package com.rsargsyan.grabberr.main_ctx.adapters.driven.transferagent;

import com.rsargsyan.grabberr.main_ctx.core.ports.client.FileTransferClient;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j
@Component
public class HttpFileTransferClient implements FileTransferClient {

  @Value("${grabberr.transfer-agent.url}")
  private String baseUrl;

  private RestClient restClient;

  @PostConstruct
  public void init() {
    this.restClient = RestClient.builder()
        .baseUrl(baseUrl)
        .build();
  }

  @Override
  public void startTransfer(String sourcePath, String s3Key) {
    restClient.post()
        .uri("/transfer")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("sourcePath", sourcePath, "s3Key", s3Key))
        .retrieve()
        .toBodilessEntity();
    log.info("startTransfer: {} -> {}", sourcePath, s3Key);
  }
}
