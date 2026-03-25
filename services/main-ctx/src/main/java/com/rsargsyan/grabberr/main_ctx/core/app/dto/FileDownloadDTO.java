package com.rsargsyan.grabberr.main_ctx.core.app.dto;

import com.rsargsyan.grabberr.main_ctx.core.domain.aggregate.FileDownload;
import com.rsargsyan.grabberr.main_ctx.core.domain.valueobject.FileDownloadStatus;

import java.time.Instant;

public record FileDownloadDTO(
    String id,
    Integer fileIndex,
    FileDownloadStatus status,
    String signedUrl,
    Long fileSizeBytes,
    Instant completedAt,
    Instant createdAt
) {
  public static FileDownloadDTO from(FileDownload fd, String signedUrl) {
    return new FileDownloadDTO(
        fd.getStrId(),
        fd.getFileIndex(),
        fd.getCachedFile().getStatus(),
        signedUrl,
        fd.getCachedFile().getFileSizeBytes(),
        fd.getCachedFile().getCompletedAt(),
        fd.getCreatedAt()
    );
  }
}
