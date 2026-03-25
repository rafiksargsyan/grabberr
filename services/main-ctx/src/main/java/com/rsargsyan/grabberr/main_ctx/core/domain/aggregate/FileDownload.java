package com.rsargsyan.grabberr.main_ctx.core.domain.aggregate;

import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"torrent_download_id", "file_index"}))
public class FileDownload extends AggregateRoot {

  @Getter
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "torrent_download_id", nullable = false)
  private TorrentDownload torrentDownload;

  @Getter
  @Column(name = "file_index", nullable = false)
  private Integer fileIndex;

  @Getter
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "cached_file_id", nullable = false)
  private CachedFile cachedFile;

  @SuppressWarnings("unused")
  FileDownload() {}

  public FileDownload(TorrentDownload torrentDownload, int fileIndex, CachedFile cachedFile) {
    this.torrentDownload = torrentDownload;
    this.fileIndex = fileIndex;
    this.cachedFile = cachedFile;
  }
}
