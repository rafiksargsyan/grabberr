package com.rsargsyan.grabberr.main_ctx.core.ports.repository;

import com.rsargsyan.grabberr.main_ctx.core.domain.aggregate.TorrentDownload;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TorrentDownloadRepository extends JpaRepository<TorrentDownload, Long> {
  Optional<TorrentDownload> findByIdAndAccount_Id(Long id, Long accountId);
  Optional<TorrentDownload> findByTorrent_InfoHashAndAccount_Id(String infoHash, Long accountId);
  List<TorrentDownload> findByAccount_IdOrderByCreatedAtDesc(Long accountId);
  boolean existsByTorrent_Id(Long torrentId);
  boolean existsByTorrent_IdAndIdNot(Long torrentId, Long excludeId);
}
