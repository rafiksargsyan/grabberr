package com.rsargsyan.grabberr.main_ctx.adapters.driven.transferagent;

import com.rsargsyan.grabberr.main_ctx.core.ports.client.FileTransferClient;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

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

  @Override
  public TransferStatusResult getTransferStatus(String sourcePath) {
    try {
      TransferStatusResponse response = restClient.get()
          .uri(u -> u.path("/transfers/status").queryParam("path", sourcePath).build())
          .retrieve()
          .body(TransferStatusResponse.class);
      if (response == null) return new TransferStatusResult(TransferStatus.UNKNOWN, null);
      return new TransferStatusResult(TransferStatus.valueOf(response.status()), response.progress());
    } catch (HttpClientErrorException e) {
      if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
        log.debug("getTransferStatus [{}]: unknown transfer (agent restarted?), will retry", sourcePath);
      } else {
        log.warn("getTransferStatus [{}]: unexpected error: {}", sourcePath, e.getMessage());
      }
      return new TransferStatusResult(TransferStatus.UNKNOWN, null);
    } catch (RestClientException e) {
      log.warn("getTransferStatus [{}]: agent unreachable: {}", sourcePath, e.getMessage());
      return new TransferStatusResult(TransferStatus.UNKNOWN, null);
    }
  }

  @Override
  public void cancelTransfer(String sourcePath) {
    try {
      restClient.post()
          .uri(u -> u.path("/transfers/cancel").queryParam("path", sourcePath).build())
          .retrieve()
          .toBodilessEntity();
      log.info("cancelTransfer: {}", sourcePath);
    } catch (HttpClientErrorException e) {
      if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
        log.debug("cancelTransfer [{}]: no active transfer (already done or agent restarted)", sourcePath);
      } else {
        log.warn("cancelTransfer [{}]: unexpected error: {}", sourcePath, e.getMessage());
      }
    } catch (RestClientException e) {
      log.warn("cancelTransfer [{}]: agent unreachable: {}", sourcePath, e.getMessage());
    }
  }

  private record TransferStatusResponse(String status, Float progress) {}
}
