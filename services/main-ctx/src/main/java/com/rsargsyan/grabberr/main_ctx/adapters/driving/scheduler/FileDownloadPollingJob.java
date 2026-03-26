package com.rsargsyan.grabberr.main_ctx.adapters.driving.scheduler;

import com.rsargsyan.grabberr.main_ctx.core.app.FileDownloadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class FileDownloadPollingJob {

  private final FileDownloadService fileDownloadService;

  @Autowired
  public FileDownloadPollingJob(FileDownloadService fileDownloadService) {
    this.fileDownloadService = fileDownloadService;
  }

  public void poll() {
    fileDownloadService.pollFileDownloads();
  }
}
