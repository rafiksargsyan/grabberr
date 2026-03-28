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
  @Getter private Long fileSizeBytes;

  @Getter private Float progress;

  @Getter private Instant downloadingAt;
  @Getter private Instant completedAt;
  @Getter private Instant lastPolledAt;

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
