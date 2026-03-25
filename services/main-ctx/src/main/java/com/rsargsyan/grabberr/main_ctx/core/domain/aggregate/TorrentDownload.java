package com.rsargsyan.grabberr.main_ctx.core.domain.aggregate;

import jakarta.persistence.*;
import lombok.Getter;

@Entity
public class TorrentDownload extends AccountScopedAggregateRoot {

  @Getter
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "torrent_id", nullable = false)
  private Torrent torrent;

  @SuppressWarnings("unused")
  TorrentDownload() {}

  public TorrentDownload(Account account, Torrent torrent) {
    super(account);
    this.torrent = torrent;
  }
}
