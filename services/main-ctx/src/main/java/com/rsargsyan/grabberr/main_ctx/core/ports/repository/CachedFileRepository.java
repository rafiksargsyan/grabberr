package com.rsargsyan.grabberr.main_ctx.core.ports.repository;

import com.rsargsyan.grabberr.main_ctx.core.domain.aggregate.CachedFile;
import com.rsargsyan.grabberr.main_ctx.core.domain.valueobject.FileDownloadStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CachedFileRepository extends JpaRepository<CachedFile, Long> {
  Optional<CachedFile> findByTorrentIdAndFileIndex(Long torrentId, Integer fileIndex);
  List<CachedFile> findByStatus(FileDownloadStatus status);
  boolean existsByTorrentIdAndStatus(Long torrentId, FileDownloadStatus status);
}
