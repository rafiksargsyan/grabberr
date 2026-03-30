package com.rsargsyan.grabberr.main_ctx.core.ports.repository;

import com.rsargsyan.grabberr.main_ctx.core.domain.aggregate.CachedFile;
import com.rsargsyan.grabberr.main_ctx.core.domain.valueobject.FileDownloadStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CachedFileRepository extends JpaRepository<CachedFile, Long> {
  List<CachedFile> findByTorrentId(Long torrentId);
  Optional<CachedFile> findByTorrentIdAndFileIndex(Long torrentId, Integer fileIndex);
  @Query("SELECT cf.id FROM CachedFile cf WHERE cf.status = :status")
  List<Long> findIdsByStatus(FileDownloadStatus status);
  boolean existsByTorrentIdAndStatus(Long torrentId, FileDownloadStatus status);
  boolean existsByTorrentIdAndStatusIn(Long torrentId, List<FileDownloadStatus> statuses);
  @Query("SELECT cf.id FROM CachedFile cf WHERE cf.status = 'DONE' AND cf.storedInS3 = true AND cf.s3ExpiresAt < :now")
  List<Long> findExpiredS3FileIds(Instant now);
  void deleteAllByTorrentId(Long torrentId);
}
