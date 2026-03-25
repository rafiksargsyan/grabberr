package com.rsargsyan.grabberr.main_ctx.scheduling;

import com.rsargsyan.grabberr.main_ctx.core.domain.aggregate.Torrent;
import com.rsargsyan.grabberr.main_ctx.core.domain.valueobject.TorrentStatus;
import com.rsargsyan.grabberr.main_ctx.core.ports.client.TorrentClient;
import com.rsargsyan.grabberr.main_ctx.core.ports.repository.TorrentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
public class MetadataPollingJob {

  private static final Logger log = LoggerFactory.getLogger(MetadataPollingJob.class);

  private final TorrentRepository torrentRepository;
  private final TorrentClient torrentClient;

  @Value("${grabberr.metadata-timeout:PT2H}")
  private Duration metadataTimeout;

  @Autowired
  public MetadataPollingJob(TorrentRepository torrentRepository,
                            TorrentClient torrentClient) {
    this.torrentRepository = torrentRepository;
    this.torrentClient = torrentClient;
  }

  @Transactional
  public void poll() {
    List<Torrent> pending = torrentRepository.findByStatus(TorrentStatus.FETCHING_METADATA);
    log.info("MetadataPollingJob: {} torrent(s) pending", pending.size());
    for (Torrent torrent : pending) {
      log.info("Polling torrent [{}]", torrent.getInfoHash());
      if (isTimedOut(torrent)) {
        log.warn("Torrent [{}] timed out, marking failed", torrent.getInfoHash());
        torrent.markFailed();
        torrentClient.removeTorrent(torrent.getInfoHash(), false);
      } else {
        var files = torrentClient.getFiles(torrent.getInfoHash());
        log.info("Torrent [{}] getFiles result: {}", torrent.getInfoHash(), files);
        files.ifPresent(torrent::markReady);
      }
      torrentRepository.save(torrent);
    }
  }

  private boolean isTimedOut(Torrent torrent) {
    return torrent.getCreatedAt().plus(metadataTimeout).isBefore(Instant.now());
  }
}
