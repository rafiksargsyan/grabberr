package com.rsargsyan.grabberr.main_ctx.core.ports.repository;

import com.rsargsyan.grabberr.main_ctx.core.domain.aggregate.FileDownload;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FileDownloadRepository extends JpaRepository<FileDownload, Long> {
  Optional<FileDownload> findByTorrentDownloadIdAndFileIndex(Long torrentDownloadId, Integer fileIndex);
}
