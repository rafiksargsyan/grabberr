package com.rsargsyan.grabberr.main_ctx.adapters.driving.scheduler;

import com.rsargsyan.grabberr.main_ctx.core.app.InfoHashSyncService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class InfoHashSyncJob {

  private final InfoHashSyncService infoHashSyncService;

  @Autowired
  public InfoHashSyncJob(InfoHashSyncService infoHashSyncService) {
    this.infoHashSyncService = infoHashSyncService;
  }

  public void sync() {
    infoHashSyncService.sync();
  }
}
