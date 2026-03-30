package com.rsargsyan.grabberr.main_ctx.core.app.dto;

import com.rsargsyan.grabberr.main_ctx.core.domain.aggregate.CachedFile;
import com.rsargsyan.grabberr.main_ctx.core.domain.valueobject.FileDownloadStatus;

import java.time.Instant;

public record FileDownloadDTO(
    String id,
    Integer fileIndex,
    FileDownloadStatus status,
    Float progress,
    String signedUrl,
    Long fileSizeBytes,
    Instant completedAt,
    Instant s3ExpiresAt,
    Instant createdAt,
    Instant downloadingAt,
    Instant downloadedAt,
    String metadata
) {
  public static FileDownloadDTO from(CachedFile cf, String signedUrl) {
    return new FileDownloadDTO(
        cf.getStrId(),
        cf.getFileIndex(),
        cf.getStatus(),
        cf.getProgress(),
        signedUrl,
        cf.getFileSizeBytes(),
        cf.getCompletedAt(),
        cf.getS3ExpiresAt(),
        cf.getCreatedAt(),
        cf.getDownloadingAt(),
        cf.getDownloadedAt(),
        cf.getMetadata()
    );
  }
}
