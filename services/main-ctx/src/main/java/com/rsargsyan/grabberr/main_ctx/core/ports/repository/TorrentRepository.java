package com.rsargsyan.grabberr.main_ctx.core.ports.repository;

import com.rsargsyan.grabberr.main_ctx.core.domain.aggregate.Torrent;
import com.rsargsyan.grabberr.main_ctx.core.domain.valueobject.TorrentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TorrentRepository extends JpaRepository<Torrent, Long> {
  Optional<Torrent> findByInfoHash(String infoHash);
  List<Torrent> findByStatus(TorrentStatus status);
}
