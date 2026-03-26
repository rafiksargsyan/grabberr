package com.rsargsyan.grabberr.main_ctx.core.domain.aggregate;

import com.rsargsyan.grabberr.main_ctx.core.domain.valueobject.TorrentFile;
import com.rsargsyan.grabberr.main_ctx.core.domain.valueobject.TorrentStatus;
import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

@Entity
public class Torrent extends AggregateRoot {

  @Getter
  @Column(nullable = false, unique = true)
  private String infoHash;

  @Getter
  private String downloadUrl; // null when submitted as an uploaded file

  @Getter
  private String torrentS3Key; // set once .torrent file is stored in S3


  @Getter
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private TorrentStatus status = TorrentStatus.FETCHING_METADATA;

  @Getter
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private List<TorrentFile> files = new ArrayList<>();

  @SuppressWarnings("unused")
  Torrent() {}

  public Torrent(String infoHash, String downloadUrl) {
    this.infoHash = infoHash;
    this.downloadUrl = downloadUrl;
  }

  public void setTorrentS3Key(String torrentS3Key) {
    this.torrentS3Key = torrentS3Key;
    touch();
  }

  public void markReady(List<TorrentFile> files) {
    this.files = new ArrayList<>(files);
    this.status = TorrentStatus.READY;
    touch();
  }

  public void markFailed() {
    this.status = TorrentStatus.FAILED;
    touch();
  }

}
