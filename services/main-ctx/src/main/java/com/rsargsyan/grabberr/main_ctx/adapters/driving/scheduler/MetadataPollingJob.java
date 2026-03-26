package com.rsargsyan.grabberr.main_ctx.adapters.driving.scheduler;

import com.rsargsyan.grabberr.main_ctx.core.app.TorrentDownloadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MetadataPollingJob {

  private final TorrentDownloadService torrentDownloadService;

  @Autowired
  public MetadataPollingJob(TorrentDownloadService torrentDownloadService) {
    this.torrentDownloadService = torrentDownloadService;
  }

  public void poll() {
    torrentDownloadService.pollMetadata();
  }
}
