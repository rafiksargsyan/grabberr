package com.rsargsyan.grabberr.main_ctx.adapters.driven.s3;

import com.rsargsyan.grabberr.main_ctx.core.ports.client.ObjectStorageClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class S3ObjectStorageClient implements ObjectStorageClient {

  @Value("${grabberr.s3.access-key-id}")
  private String accessKeyId;

  @Value("${grabberr.s3.secret-access-key}")
  private String secretAccessKey;

  @Value("${grabberr.s3.region}")
  private String region;

  @Value("${grabberr.s3.endpoint}")
  private String endpoint;

  @Value("${grabberr.s3.bucket}")
  private String bucket;

  @Override
  public void upload(String path, byte[] bytes) {
    // TODO: PUT {endpoint}/{bucket}/{path} with bytes
    throw new UnsupportedOperationException("Not yet implemented");
  }

  @Override
  public String generateSignedUrl(String path) {
    // TODO: generate pre-signed GET URL for {bucket}/{path}
    throw new UnsupportedOperationException("Not yet implemented");
  }
}
