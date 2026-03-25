package com.rsargsyan.grabberr.main_ctx.core.app.dto;

import com.rsargsyan.grabberr.main_ctx.core.domain.aggregate.TorrentDownload;
import com.rsargsyan.grabberr.main_ctx.core.domain.valueobject.TorrentFile;
import com.rsargsyan.grabberr.main_ctx.core.domain.valueobject.TorrentStatus;

import java.time.Instant;
import java.util.List;

public record TorrentDownloadDTO(
    String id,
    String infoHash,
    TorrentStatus status,
    List<TorrentFile> files,
    Instant createdAt
) {
  public static TorrentDownloadDTO from(TorrentDownload td) {
    return new TorrentDownloadDTO(
        td.getStrId(),
        td.getTorrent().getInfoHash(),
        td.getTorrent().getStatus(),
        td.getTorrent().getFiles(),
        td.getCreatedAt()
    );
  }
}
