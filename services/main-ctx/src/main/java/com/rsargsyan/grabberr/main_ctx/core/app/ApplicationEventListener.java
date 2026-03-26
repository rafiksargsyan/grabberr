package com.rsargsyan.grabberr.main_ctx.core.app;

import com.rsargsyan.grabberr.main_ctx.core.Util;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class ApplicationEventListener {

  private final FileDownloadService fileDownloadService;

  @Autowired
  public ApplicationEventListener(FileDownloadService fileDownloadService) {
    this.fileDownloadService = fileDownloadService;
  }

  @Async
  @TransactionalEventListener
  public void onCachedFileSubmitted(CachedFileSubmittedEvent event) {
    String strId = Util.tsidToString(event.cachedFileId());
    try {
      fileDownloadService.processFileDownload(strId);
    } catch (Exception e) {
      log.error("Failed to process submitted cached file [{}]", strId, e);
    }
  }
}
