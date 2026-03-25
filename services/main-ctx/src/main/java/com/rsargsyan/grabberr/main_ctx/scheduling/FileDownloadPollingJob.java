package com.rsargsyan.grabberr.main_ctx.scheduling;

import com.rsargsyan.grabberr.main_ctx.core.domain.aggregate.CachedFile;
import com.rsargsyan.grabberr.main_ctx.core.domain.valueobject.FileDownloadStatus;
import com.rsargsyan.grabberr.main_ctx.core.ports.client.FileServerClient;
import com.rsargsyan.grabberr.main_ctx.core.ports.client.ObjectStorageClient;
import com.rsargsyan.grabberr.main_ctx.core.ports.client.TorrentClient;
import com.rsargsyan.grabberr.main_ctx.core.ports.repository.CachedFileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
public class FileDownloadPollingJob {

  private final CachedFileRepository cachedFileRepository;
  private final TorrentClient torrentClient;
  private final FileServerClient fileServerClient;
  private final ObjectStorageClient objectStorageClient;

  @Value("${grabberr.download-timeout:PT24H}")
  private Duration downloadTimeout;

  @Autowired
  public FileDownloadPollingJob(CachedFileRepository cachedFileRepository,
                                TorrentClient torrentClient,
                                FileServerClient fileServerClient,
                                ObjectStorageClient objectStorageClient) {
    this.cachedFileRepository = cachedFileRepository;
    this.torrentClient = torrentClient;
    this.fileServerClient = fileServerClient;
    this.objectStorageClient = objectStorageClient;
  }

  @Transactional
  public void poll() {
    List<CachedFile> active = cachedFileRepository.findByStatus(FileDownloadStatus.DOWNLOADING);
    for (CachedFile cf : active) {
      cf.recordPoll();
      if (isTimedOut(cf)) {
        cf.markFailed();
      } else {
        String infoHash = cf.getTorrent().getInfoHash();
        float progress = torrentClient.getFileProgress(infoHash, cf.getFileIndex());
        if (progress >= 1.0f) {
          String relativePath = torrentClient.getRelativeFilePath(infoHash, cf.getFileIndex());
          byte[] bytes = fileServerClient.download(relativePath);
          objectStorageClient.upload(relativePath, bytes);
          cf.markDone(relativePath, (long) bytes.length);
        }
      }
      cachedFileRepository.save(cf);
      if (cf.getStatus() != FileDownloadStatus.DOWNLOADING) {
        cleanUpTorrentIfDone(cf);
      }
    }
  }

  private void cleanUpTorrentIfDone(CachedFile cf) {
    boolean anyActive = cachedFileRepository.existsByTorrentIdAndStatus(
        cf.getTorrent().getId(), FileDownloadStatus.DOWNLOADING);
    if (!anyActive) {
      torrentClient.removeTorrent(cf.getTorrent().getInfoHash(), true);
    }
  }

  private boolean isTimedOut(CachedFile cf) {
    return cf.getCreatedAt().plus(downloadTimeout).isBefore(Instant.now());
  }
}
