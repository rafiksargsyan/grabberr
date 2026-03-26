package com.rsargsyan.grabberr.main_ctx.core.domain.aggregate;

import com.rsargsyan.grabberr.main_ctx.core.domain.valueobject.FileDownloadStatus;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.Duration;
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
  @Getter private Long fileSizeBytes;

  @Getter private Float progress;
  @Getter private Long etaSeconds;
  private Instant lastProgressAt;

  @Getter private Instant completedAt;
  @Getter private Instant lastPolledAt;

  @SuppressWarnings("unused")
  CachedFile() {}

  public CachedFile(Torrent torrent, int fileIndex) {
    this.torrent = torrent;
    this.fileIndex = fileIndex;
  }

  public void updateProgress(float newProgress, long totalSizeBytes) {
    Instant now = Instant.now();
    if (lastProgressAt != null && progress != null && newProgress > progress && totalSizeBytes > 0) {
      double elapsedSeconds = Duration.between(lastProgressAt, now).toMillis() / 1000.0;
      if (elapsedSeconds > 0) {
        double bytesPerSec = (newProgress - progress) * totalSizeBytes / elapsedSeconds;
        double remainingBytes = (1.0 - newProgress) * totalSizeBytes;
        this.etaSeconds = bytesPerSec > 0 ? (long) (remainingBytes / bytesPerSec) : null;
      }
    }
    this.progress = newProgress;
    this.lastProgressAt = now;
    touch();
  }

  public void markDownloading() {
    this.status = FileDownloadStatus.DOWNLOADING;
    touch();
  }

  public void markTransferring(String path) {
    this.status = FileDownloadStatus.TRANSFERRING;
    this.path = path;
    touch();
  }

  public void markDone(String path, Long fileSizeBytes) {
    this.status = FileDownloadStatus.DONE;
    this.path = path;
    this.fileSizeBytes = fileSizeBytes;
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
