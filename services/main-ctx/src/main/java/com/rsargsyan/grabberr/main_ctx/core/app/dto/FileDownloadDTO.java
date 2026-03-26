package com.rsargsyan.grabberr.main_ctx.core.app.dto;

import com.rsargsyan.grabberr.main_ctx.core.domain.aggregate.CachedFile;
import com.rsargsyan.grabberr.main_ctx.core.domain.valueobject.FileDownloadStatus;

import java.time.Instant;

public record FileDownloadDTO(
    String id,
    Integer fileIndex,
    FileDownloadStatus status,
    Float progress,
    Long etaSeconds,
    String signedUrl,
    Long fileSizeBytes,
    Instant completedAt,
    Instant createdAt
) {
  public static FileDownloadDTO from(CachedFile cf, String signedUrl) {
    return new FileDownloadDTO(
        cf.getStrId(),
        cf.getFileIndex(),
        cf.getStatus(),
        cf.getProgress(),
        cf.getEtaSeconds(),
        signedUrl,
        cf.getFileSizeBytes(),
        cf.getCompletedAt(),
        cf.getCreatedAt()
    );
  }
}
