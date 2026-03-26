package com.rsargsyan.grabberr.main_ctx.adapters.driven.s3;

import com.rsargsyan.grabberr.main_ctx.core.ports.client.ObjectStorageClient;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

@Slf4j
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

  @Value("${grabberr.s3.signed-url-expiry:PT24H}")
  private Duration signedUrlExpiry;

  private S3Client s3Client;
  private S3Presigner s3Presigner;

  @PostConstruct
  public void init() {
    var credentials = StaticCredentialsProvider.create(
        AwsBasicCredentials.create(accessKeyId, secretAccessKey));
    URI endpointUri = URI.create(endpoint.contains("://") ? endpoint : "https://" + endpoint);

    s3Client = S3Client.builder()
        .credentialsProvider(credentials)
        .region(Region.of(region))
        .endpointOverride(endpointUri)
        .build();

    s3Presigner = S3Presigner.builder()
        .credentialsProvider(credentials)
        .region(Region.of(region))
        .endpointOverride(endpointUri)
        .build();
  }

  @Override
  public void upload(String key, byte[] bytes, String contentType) {
    s3Client.putObject(
        PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
        RequestBody.fromBytes(bytes)
    );
  }

  @Override
  public byte[] download(String key) {
    return s3Client.getObjectAsBytes(
        GetObjectRequest.builder().bucket(bucket).key(key).build()
    ).asByteArray();
  }

  @Override
  public Optional<Long> getSize(String path) {
    try {
      var response = s3Client.headObject(
          HeadObjectRequest.builder().bucket(bucket).key(path).build()
      );
      return Optional.of(response.contentLength());
    } catch (NoSuchKeyException e) {
      return Optional.empty();
    }
  }

  @Override
  public String generateSignedUrl(String path) {
    return s3Presigner.presignGetObject(
        GetObjectPresignRequest.builder()
            .signatureDuration(signedUrlExpiry)
            .getObjectRequest(GetObjectRequest.builder()
                .bucket(bucket)
                .key(path)
                .build())
            .build()
    ).url().toString();
  }
}
