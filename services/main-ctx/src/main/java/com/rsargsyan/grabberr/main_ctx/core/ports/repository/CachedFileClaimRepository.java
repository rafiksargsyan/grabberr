package com.rsargsyan.grabberr.main_ctx.core.ports.repository;

import com.rsargsyan.grabberr.main_ctx.core.domain.aggregate.CachedFileClaim;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CachedFileClaimRepository extends JpaRepository<CachedFileClaim, Long> {
  Optional<CachedFileClaim> findByCachedFile_IdAndAccount_Id(Long cachedFileId, Long accountId);
  boolean existsByCachedFile_Id(Long cachedFileId);
  void deleteByAccount_IdAndCachedFile_TorrentId(Long accountId, Long torrentId);
  void deleteByCachedFile_TorrentId(Long torrentId);
}
