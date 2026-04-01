package com.rsargsyan.grabberr.main_ctx.core.domain.aggregate;

import com.rsargsyan.grabberr.main_ctx.core.domain.valueobject.FileDownloadStatus;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"torrent_id", "file_index"}))
public class CachedFile extends AggregateRoot {

  @Getter
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "torrent_id", nullable = false)
  private Torrent torrent;

  @Getter
  @Column(name = "file_index", nullable = false)
  private Integer fileIndex;

  @Getter
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private FileDownloadStatus status = FileDownloadStatus.SUBMITTED;

  // populated when DONE
  @Getter private String path;
  @Getter @Column(name = "s3_key") private String s3Key;
  @Getter private Long fileSizeBytes;
  @Getter private boolean storedInS3;

  @Getter private Float progress;

  // populated when DOWNLOADED
  @Getter @Column(columnDefinition = "text") private String metadata;
  @Getter private Instant downloadedAt;

  @Getter private Instant downloadingAt;
  @Getter private Instant completedAt;
  @Getter private Instant lastPolledAt;
  @Getter private Instant transferringStartedAt;
  @Getter @Column(nullable = false, columnDefinition = "integer default 0") private int transferRetryCount;
  @Getter private Instant s3ExpiresAt;

  @SuppressWarnings("unused")
  CachedFile() {}

  public CachedFile(Torrent torrent, int fileIndex) {
    this.torrent = torrent;
    this.fileIndex = fileIndex;
  }

  public void updateProgress(float newProgress) {
    this.progress = newProgress;
    touch();
  }

  public void markDownloading() {
    this.status = FileDownloadStatus.DOWNLOADING;
    this.downloadingAt = Instant.now();
    touch();
  }

  public void markDownloaded(String path, String metadata) {
    this.status = FileDownloadStatus.DOWNLOADED;
    this.path = path;
    this.metadata = metadata;
    this.downloadedAt = Instant.now();
    touch();
  }

  public void markTransferring(String path, String s3Key) {
    this.status = FileDownloadStatus.TRANSFERRING;
    this.path = path;
    this.s3Key = s3Key;
    this.transferringStartedAt = Instant.now();
    this.transferRetryCount = 0;
    this.progress = null;
    touch();
  }

  public void retryTransfer() {
    this.transferringStartedAt = Instant.now();
    this.transferRetryCount++;
    touch();
  }

  public void markDone(String path, Long fileSizeBytes, Instant s3ExpiresAt) {
    this.status = FileDownloadStatus.DONE;
    this.path = path;
    this.fileSizeBytes = fileSizeBytes;
    this.storedInS3 = true;
    this.completedAt = Instant.now();
    this.s3ExpiresAt = s3ExpiresAt;
    touch();
  }

  public void expireS3() {
    this.storedInS3 = false;
    this.s3ExpiresAt = null;
    touch();
  }

  public void resetForReclaim() {
    this.status = FileDownloadStatus.SUBMITTED;
    this.storedInS3 = false;
    this.completedAt = null;
    this.s3ExpiresAt = null;
    touch();
  }

  public void extendS3Expiry(Instant newExpiry) {
    this.s3ExpiresAt = newExpiry;
    touch();
  }

  public void markDoneLocal(String path, Long fileSizeBytes) {
    this.status = FileDownloadStatus.DONE;
    this.path = path;
    this.fileSizeBytes = fileSizeBytes;
    this.storedInS3 = false;
    this.s3ExpiresAt = null;
    this.completedAt = Instant.now();
    touch();
  }

  public void markFailed() {
    this.status = FileDownloadStatus.FAILED;
    touch();
  }

  public void recordPoll() {
    this.lastPolledAt = Instant.now();
    touch();
  }
}
