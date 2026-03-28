package com.rsargsyan.grabberr.main_ctx.core.ports.repository;

import com.rsargsyan.grabberr.main_ctx.core.domain.aggregate.Torrent;
import com.rsargsyan.grabberr.main_ctx.core.domain.valueobject.TorrentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TorrentRepository extends JpaRepository<Torrent, Long> {
  Optional<Torrent> findByInfoHash(String infoHash);
  @Query("SELECT t.id FROM Torrent t WHERE t.status = :status")
  List<Long> findIdsByStatus(TorrentStatus status);
  @Query("SELECT t.id FROM Torrent t WHERE t.status IN :statuses")
  List<Long> findIdsByStatusIn(List<TorrentStatus> statuses);
}
