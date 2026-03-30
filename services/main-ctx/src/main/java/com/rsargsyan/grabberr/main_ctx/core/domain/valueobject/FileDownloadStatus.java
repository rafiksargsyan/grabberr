package com.rsargsyan.grabberr.main_ctx.core.domain.valueobject;

public enum FileDownloadStatus {
  SUBMITTED,
  DOWNLOADING,
  DOWNLOADED,
  TRANSFERRING, //TODO: rename transferring to caching
  DONE,
  FAILED
}
