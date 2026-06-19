package com.rsargsyan.grabberr.main_ctx.core.app;

import com.rsargsyan.grabberr.main_ctx.core.ports.client.TorrentClient;
import com.rsargsyan.grabberr.main_ctx.core.ports.repository.TorrentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
public class InfoHashSyncService {

  private final TorrentRepository torrentRepository;
  private final TorrentClient torrentClient;

  @Lazy
  @Autowired
  private InfoHashSyncService self;

  @Autowired
  public InfoHashSyncService(TorrentRepository torrentRepository, TorrentClient torrentClient) {
    this.torrentRepository = torrentRepository;
    this.torrentClient = torrentClient;
  }

  public void sync() {
    Map<String, String> v2Map = torrentClient.getInfoHashV2Map();
    if (v2Map.isEmpty()) return;
    log.info("InfoHashSync: {} torrent(s) with v2 hash in qBittorrent", v2Map.size());
    for (Map.Entry<String, String> entry : v2Map.entrySet()) {
      log.info("InfoHashSync: v1=[{}] truncated_v2=[{}]", entry.getKey(), entry.getValue());
      try {
        self.updateV2Hash(entry.getKey(), entry.getValue());
      } catch (Exception e) {
        log.error("InfoHashSync: failed to update v2 hash for [{}]", entry.getKey(), e);
      }
    }
  }

  @Transactional
  public void updateV2Hash(String infoHash, String infoHashV2) {
    var torrent = torrentRepository.findByInfoHash(infoHash);
    log.info("InfoHashSync: findByInfoHash([{}]) -> {}", infoHash, torrent.isPresent() ? "found" : "not found");
    torrent.ifPresent(t -> {
      if (t.getInfoHashV2() == null) {
        t.setInfoHashV2(infoHashV2);
        torrentRepository.save(t);
        log.info("InfoHashSync: [{}] -> v2 [{}]", infoHash, infoHashV2);
      } else {
        log.info("InfoHashSync: [{}] already has v2 [{}], skipping", infoHash, t.getInfoHashV2());
      }
    });
  }
}
