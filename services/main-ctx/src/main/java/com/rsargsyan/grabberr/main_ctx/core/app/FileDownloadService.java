package com.rsargsyan.grabberr.main_ctx.core.app;

import com.rsargsyan.grabberr.main_ctx.core.Util;
import com.rsargsyan.grabberr.main_ctx.core.domain.service.TSIDValidator;
import com.rsargsyan.grabberr.main_ctx.core.app.dto.FileDownloadDTO;
import com.rsargsyan.grabberr.main_ctx.core.domain.aggregate.CachedFile;
import com.rsargsyan.grabberr.main_ctx.core.domain.aggregate.Torrent;
import com.rsargsyan.grabberr.main_ctx.core.domain.valueobject.TorrentFile;
import com.rsargsyan.grabberr.main_ctx.core.domain.aggregate.TorrentDownload;
import com.rsargsyan.grabberr.main_ctx.core.domain.valueobject.FileDownloadStatus;
import com.rsargsyan.grabberr.main_ctx.core.domain.valueobject.TorrentStatus;
import com.rsargsyan.grabberr.main_ctx.core.exception.InvalidFileIndexException;
import com.rsargsyan.grabberr.main_ctx.core.exception.ResourceNotFoundException;
import com.rsargsyan.grabberr.main_ctx.core.exception.TorrentNotReadyException;
import com.rsargsyan.grabberr.main_ctx.core.ports.client.FileTransferClient;
import com.rsargsyan.grabberr.main_ctx.core.ports.client.ObjectStorageClient;
import com.rsargsyan.grabberr.main_ctx.core.ports.client.TorrentClient;
import com.rsargsyan.grabberr.main_ctx.core.domain.aggregate.Account;
import com.rsargsyan.grabberr.main_ctx.core.domain.aggregate.CachedFileClaim;
import com.rsargsyan.grabberr.main_ctx.core.ports.repository.AccountRepository;
import com.rsargsyan.grabberr.main_ctx.core.ports.repository.CachedFileClaimRepository;
import com.rsargsyan.grabberr.main_ctx.core.ports.repository.CachedFileRepository;
import com.rsargsyan.grabberr.main_ctx.core.ports.repository.TorrentDownloadRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
public class FileDownloadService {

  private static final List<FileDownloadStatus> ACTIVE_STATUSES = List.of(
      FileDownloadStatus.SUBMITTED, FileDownloadStatus.DOWNLOADING,
      FileDownloadStatus.DOWNLOADED, FileDownloadStatus.TRANSFERRING);

  @Value("${grabberr.download-timeout:PT24H}")
  private Duration downloadTimeout;

  @Value("${grabberr.transfer-min-speed-bps:1048576}")
  private long transferMinSpeedBps;

  @Value("${grabberr.transfer-backoff-initial-delay:PT1M}")
  private Duration transferBackoffInitialDelay;

  @Value("${grabberr.transfer-backoff-max-delay:PT30M}")
  private Duration transferBackoffMaxDelay;

  @Value("${grabberr.min-free-space-bytes:10737418240}")
  private long minFreeSpaceBytes;

  @Value("${grabberr.downloads-base-url}")
  private String downloadsBaseUrl;

  @Value("${grabberr.downloaded-file-ttl:PT1H}")
  private Duration downloadedFileTtl;

  @Value("${grabberr.cache-ttl:P15D}")
  private Duration cacheTtl;

  private final TorrentDownloadRepository torrentDownloadRepository;
  private final CachedFileRepository cachedFileRepository;
  private final CachedFileClaimRepository cachedFileClaimRepository;
  private final AccountRepository accountRepository;
  private final TorrentClient torrentClient;
  private final FileTransferClient fileTransferClient;
  private final ObjectStorageClient objectStorageClient;
  private final ApplicationEventPublisher eventPublisher;

  @Lazy
  @Autowired
  private FileDownloadService self;

  @Autowired
  public FileDownloadService(TorrentDownloadRepository torrentDownloadRepository,
                             CachedFileRepository cachedFileRepository,
                             CachedFileClaimRepository cachedFileClaimRepository,
                             AccountRepository accountRepository,
                             TorrentClient torrentClient,
                             FileTransferClient fileTransferClient,
                             ObjectStorageClient objectStorageClient,
                             ApplicationEventPublisher eventPublisher) {
    this.torrentDownloadRepository = torrentDownloadRepository;
    this.cachedFileRepository = cachedFileRepository;
    this.cachedFileClaimRepository = cachedFileClaimRepository;
    this.accountRepository = accountRepository;
    this.torrentClient = torrentClient;
    this.fileTransferClient = fileTransferClient;
    this.objectStorageClient = objectStorageClient;
    this.eventPublisher = eventPublisher;
  }

  @Transactional
  public FileDownloadDTO claim(String torrentDownloadIdStr, int fileIndex, String accountIdStr) {
    TorrentDownload torrentDownload = findTorrentDownload(torrentDownloadIdStr, accountIdStr);

    if (torrentDownload.getTorrent().getStatus() != TorrentStatus.READY) {
      throw new TorrentNotReadyException();
    }

    boolean validIndex = torrentDownload.getTorrent().getFiles().stream()
        .anyMatch(f -> f.index() == fileIndex);
    if (!validIndex) {
      throw new InvalidFileIndexException();
    }

    Account account = accountRepository.findById(TSIDValidator.validate(accountIdStr))
        .orElseThrow(ResourceNotFoundException::new);

    CachedFile cachedFile = cachedFileRepository
        .findByTorrentIdAndFileIndex(torrentDownload.getTorrent().getId(), fileIndex)
        .orElseGet(() -> {
          CachedFile cf = new CachedFile(torrentDownload.getTorrent(), fileIndex);
          cachedFileRepository.save(cf);
          eventPublisher.publishEvent(new CachedFileSubmittedEvent(cf.getId()));
          return cf;
        });

    if (cachedFile.getStatus() == FileDownloadStatus.DONE && !cachedFile.isStoredInS3()) {
      cachedFile.resetForReclaim();
      cachedFileRepository.save(cachedFile);
      eventPublisher.publishEvent(new CachedFileSubmittedEvent(cachedFile.getId()));
    }

    cachedFileClaimRepository.findByCachedFile_IdAndAccount_Id(cachedFile.getId(), account.getId())
        .orElseGet(() -> cachedFileClaimRepository.save(new CachedFileClaim(account, cachedFile)));

    return toDTO(cachedFile);
  }

  @Transactional
  public void unclaim(String torrentDownloadIdStr, int fileIndex, String accountIdStr) {
    TorrentDownload torrentDownload = findTorrentDownload(torrentDownloadIdStr, accountIdStr);
    Long accountId = TSIDValidator.validate(accountIdStr);

    cachedFileRepository
        .findByTorrentIdAndFileIndex(torrentDownload.getTorrent().getId(), fileIndex)
        .ifPresent(cf -> {
          cachedFileClaimRepository.findByCachedFile_IdAndAccount_Id(cf.getId(), accountId)
              .ifPresent(cachedFileClaimRepository::delete);
          cancelIfNoClaims(cf);
        });
  }

  @Transactional
  public FileDownloadDTO cacheFile(String torrentDownloadIdStr, int fileIndex, String accountIdStr) {
    TorrentDownload torrentDownload = findTorrentDownload(torrentDownloadIdStr, accountIdStr);
    CachedFile cf = cachedFileRepository
        .findByTorrentIdAndFileIndex(torrentDownload.getTorrent().getId(), fileIndex)
        .orElseThrow(ResourceNotFoundException::new);
    if (cf.getStatus() != FileDownloadStatus.DOWNLOADED) {
      return toDTO(cf);
    }
    try {
      String s3Key = cf.getTorrent().getInfoHash() + "/" + cf.getPath();
      fileTransferClient.startTransfer(cf.getPath(), s3Key);
      cf.markTransferring(cf.getPath(), s3Key);
      cachedFileRepository.save(cf);
    } catch (Exception e) {
      log.warn("startTransfer [{}]: failed, will retry", cf.getPath(), e);
    }
    return toDTO(cf);
  }

  public List<FileDownloadDTO> list(String torrentDownloadIdStr, String accountIdStr) {
    TorrentDownload torrentDownload = findTorrentDownload(torrentDownloadIdStr, accountIdStr);
    return cachedFileRepository.findByTorrentId(torrentDownload.getTorrent().getId()).stream()
        .map(this::toDTO)
        .toList();
  }

  public FileDownloadDTO getStatus(String torrentDownloadIdStr, int fileIndex, String accountIdStr) {
    TorrentDownload torrentDownload = findTorrentDownload(torrentDownloadIdStr, accountIdStr);
    Long accountId = TSIDValidator.validate(accountIdStr);
    return cachedFileRepository
        .findByTorrentIdAndFileIndex(torrentDownload.getTorrent().getId(), fileIndex)
        .filter(cf -> cachedFileClaimRepository.findByCachedFile_IdAndAccount_Id(cf.getId(), accountId).isPresent())
        .map(this::toDTO)
        .orElseThrow(ResourceNotFoundException::new);
  }

  @Transactional
  public void deleteAllForTorrent(Long torrentId) {
    cachedFileClaimRepository.deleteByCachedFile_TorrentId(torrentId);
    cachedFileRepository.findByTorrentId(torrentId).forEach(cf -> {
      if (cf.isStoredInS3()) {
        try {
          objectStorageClient.delete(s3Key(cf));
        } catch (Exception e) {
          log.warn("Failed to delete S3 object [{}] for cachedFile=[{}]", s3Key(cf), cf.getId(), e);
        }
      }
    });
    cachedFileRepository.deleteAllByTorrentId(torrentId);
  }

  @Transactional
  public void cancelClaimsForAccount(Long torrentId, Long accountId) {
    cachedFileClaimRepository.deleteByAccount_IdAndCachedFile_TorrentId(accountId, torrentId);
    cachedFileRepository.findByTorrentId(torrentId).forEach(this::cancelIfNoClaims);
  }

  private void cancelIfNoClaims(CachedFile cf) {
    if (cf.getStatus() == FileDownloadStatus.DONE) return;
    if (!cachedFileClaimRepository.existsByCachedFile_Id(cf.getId())) {
      if (cf.getStatus() == FileDownloadStatus.TRANSFERRING) {
        log.info("CachedFile [{}] has no remaining claims, cancelling transfer", cf.getId());
        fileTransferClient.cancelTransfer(cf.getPath());
      } else {
        log.info("CachedFile [{}] has no remaining claims, deleting", cf.getId());
      }
      cachedFileRepository.delete(cf);
      boolean anyActive = cachedFileRepository.existsByTorrentIdAndStatusIn(cf.getTorrent().getId(), ACTIVE_STATUSES);
      if (!anyActive) {
        torrentClient.removeTorrent(cf.getTorrent().getInfoHash(), true);
      }
    }
  }

  @Transactional
  public void extendCacheLifetime(String torrentDownloadIdStr, int fileIndex, String accountIdStr, int days) {
    TorrentDownload torrentDownload = findTorrentDownload(torrentDownloadIdStr, accountIdStr);
    CachedFile cf = cachedFileRepository
        .findByTorrentIdAndFileIndex(torrentDownload.getTorrent().getId(), fileIndex)
        .orElseThrow(ResourceNotFoundException::new);
    if (cf.getStatus() != FileDownloadStatus.DONE || !cf.isStoredInS3()) return;
    Instant currentExpiry = cf.getS3ExpiresAt() != null ? cf.getS3ExpiresAt() : Instant.now();
    cf.extendS3Expiry(currentExpiry.plus(Duration.ofDays(days)));
    cachedFileRepository.save(cf);
  }

  public void pollExpiredCachedFiles() {
    for (Long id : cachedFileRepository.findExpiredS3FileIds(Instant.now())) {
      String strId = Util.tsidToString(id);
      try {
        self.expireCachedFile(strId);
      } catch (Exception e) {
        log.error("Failed to expire cached file [{}]", strId, e);
      }
    }
  }

  @Transactional
  public void expireCachedFile(String cachedFileIdStr) {
    Long cachedFileId = TSIDValidator.validate(cachedFileIdStr);
    CachedFile cf = cachedFileRepository.findById(cachedFileId).orElse(null);
    if (cf == null || cf.getStatus() != FileDownloadStatus.DONE || !cf.isStoredInS3()) return;
    try {
      objectStorageClient.delete(s3Key(cf));
    } catch (Exception e) {
      log.warn("Failed to delete S3 object [{}] for cachedFile=[{}]", s3Key(cf), cachedFileId, e);
      return;
    }
    cf.expireS3();
    cachedFileRepository.save(cf);
    log.info("Expired S3 object for cachedFile=[{}] s3Key=[{}]", cachedFileId, s3Key(cf));
  }

  public void pollFileDownloads() {
    pollExpiredCachedFiles();
    for (Long id : cachedFileRepository.findIdsByStatus(FileDownloadStatus.SUBMITTED)) {
      String strId = Util.tsidToString(id);
      try {
        self.processFileDownload(strId);
      } catch (Exception e) {
        log.error("Failed to process submitted cached file [{}]", strId, e);
      }
    }
    for (Long id : cachedFileRepository.findIdsByStatus(FileDownloadStatus.DOWNLOADING)) {
      String strId = Util.tsidToString(id);
      try {
        self.processCachedFileDownload(strId);
      } catch (Exception e) {
        log.error("Failed to process cached file download [{}]", strId, e);
      }
    }
    for (Long id : cachedFileRepository.findIdsByStatus(FileDownloadStatus.DOWNLOADED)) {
      String strId = Util.tsidToString(id);
      try {
        self.pollDownloadedFile(strId);
      } catch (Exception e) {
        log.error("Failed to poll downloaded file [{}]", strId, e);
      }
    }
    for (Long id : cachedFileRepository.findIdsByStatus(FileDownloadStatus.TRANSFERRING)) {
      String strId = Util.tsidToString(id);
      try {
        self.pollTransferringFile(strId);
      } catch (Exception e) {
        log.error("Failed to poll transferring file [{}]", strId, e);
      }
    }
  }

  @Transactional
  public void processFileDownload(String cachedFileIdStr) {
    Long cachedFileId = TSIDValidator.validate(cachedFileIdStr);
    CachedFile cf = cachedFileRepository.findById(cachedFileId).orElse(null);
    if (cf == null || cf.getStatus() != FileDownloadStatus.SUBMITTED) return;
    cf.recordPoll();
    if (cf.getCreatedAt().plus(downloadTimeout).isBefore(Instant.now())) {
      cf.markFailed();
      cachedFileRepository.save(cf);
      return;
    }
    Torrent torrent = cf.getTorrent();
    if (torrent.getStatus() != TorrentStatus.READY) {
      cachedFileRepository.save(cf);
      return;
    }
    if (torrentClient.getFiles(torrent.getInfoHash()).isEmpty()) {
      log.warn("Torrent [{}] missing from qBittorrent — re-adding", torrent.getInfoHash());
      if (torrent.getTorrentS3Key() != null) {
        torrentClient.addTorrent(torrent.getInfoHash(), objectStorageClient.download(torrent.getTorrentS3Key()));
      } else if (torrent.getDownloadUrl() != null) {
        torrentClient.addTorrent(torrent.getInfoHash(), torrent.getDownloadUrl());
      }
      List<Integer> allIndices = torrent.getFiles().stream().map(TorrentFile::index).toList();
      torrentClient.disableAllFiles(torrent.getInfoHash(), allIndices);
    }
    long fileSize = torrent.getFiles().stream()
        .filter(f -> f.index() == cf.getFileIndex())
        .mapToLong(com.rsargsyan.grabberr.main_ctx.core.domain.valueobject.TorrentFile::sizeBytes)
        .findFirst().orElse(0L);
    long freeSpace = torrentClient.getFreeSpaceBytes();
    if (freeSpace - fileSize < minFreeSpaceBytes) {
      log.warn("Not enough disk space to enable file for cachedFile=[{}]: freeSpace={} fileSize={} minFree={}",
          cf.getId(), freeSpace, fileSize, minFreeSpaceBytes);
      cachedFileRepository.save(cf);
      return;
    }
    torrentClient.enableFile(torrent.getInfoHash(), cf.getFileIndex());
    cf.markDownloading();
    cachedFileRepository.save(cf);
  }

  @Transactional
  public void pollTransferringFile(String cachedFileIdStr) {
    Long cachedFileId = TSIDValidator.validate(cachedFileIdStr);
    CachedFile cf = cachedFileRepository.findById(cachedFileId).orElse(null);
    if (cf == null || cf.getStatus() != FileDownloadStatus.TRANSFERRING) return;
    cf.recordPoll();

    long fileSizeBytes = cf.getTorrent().getFiles().stream()
        .filter(f -> f.index() == cf.getFileIndex())
        .mapToLong(TorrentFile::sizeBytes)
        .findFirst().orElse(0L);

    Duration transferTimeout = fileSizeBytes > 0
        ? Duration.ofSeconds(fileSizeBytes / transferMinSpeedBps)
        : downloadTimeout;

    Instant startedAt = cf.getTransferringStartedAt() != null ? cf.getTransferringStartedAt() : cf.getCreatedAt();
    if (startedAt.plus(transferTimeout).isBefore(Instant.now())) {
      log.warn("Transfer timeout for cachedFile=[{}] after {}s, marking FAILED", cf.getId(), transferTimeout.toSeconds());
      cf.markFailed();
      cachedFileRepository.save(cf);
      torrentClient.removeTorrent(cf.getTorrent().getInfoHash(), true);
      return;
    }

    FileTransferClient.TransferStatusResult result = fileTransferClient.getTransferStatus(cf.getPath());
    if (result.status() == FileTransferClient.TransferStatus.DONE) {
      cf.markDone(cf.getPath(), fileSizeBytes, Instant.now().plus(cacheTtl));
    } else if (result.status() == FileTransferClient.TransferStatus.RUNNING) {
      if (result.progress() != null) {
        cf.updateProgress(result.progress());
      }
    } else if (result.status() == FileTransferClient.TransferStatus.FAILED
        || result.status() == FileTransferClient.TransferStatus.UNKNOWN) {
      long backoffSeconds = Math.min(
          transferBackoffInitialDelay.toSeconds() * (1L << cf.getTransferRetryCount()),
          transferBackoffMaxDelay.toSeconds());
      if (startedAt.plusSeconds(backoffSeconds).isBefore(Instant.now())) {
        log.info("Retrying transfer for cachedFile=[{}] (attempt {})", cf.getId(), cf.getTransferRetryCount() + 1);
        try {
          fileTransferClient.startTransfer(cf.getPath(), cf.getS3Key());
          cf.retryTransfer();
        } catch (Exception e) {
          log.warn("startTransfer retry [{}]: failed", cf.getPath(), e);
        }
      }
    }

    cachedFileRepository.save(cf);
    if (cf.getStatus() != FileDownloadStatus.TRANSFERRING) {
      boolean anyActive = cachedFileRepository.existsByTorrentIdAndStatusIn(cf.getTorrent().getId(), ACTIVE_STATUSES);
      if (!anyActive) {
        torrentClient.removeTorrent(cf.getTorrent().getInfoHash(), true);
      }
    }
  }

  @Transactional
  public void processCachedFileDownload(String cachedFileIdStr) {
    Long cachedFileId = TSIDValidator.validate(cachedFileIdStr);
    CachedFile cf = cachedFileRepository.findById(cachedFileId).orElse(null);
    if (cf == null || cf.getStatus() != FileDownloadStatus.DOWNLOADING) return;
    cf.recordPoll();
    if (cf.getCreatedAt().plus(downloadTimeout).isBefore(Instant.now())) {
      cf.markFailed();
    } else {
      String infoHash = cf.getTorrent().getInfoHash();
      if (torrentClient.getFiles(infoHash).isEmpty()) {
        log.warn("Torrent [{}] missing from qBittorrent during download — re-adding", infoHash);
        Torrent torrent = cf.getTorrent();
        if (torrent.getTorrentS3Key() != null) {
          torrentClient.addTorrent(infoHash, objectStorageClient.download(torrent.getTorrentS3Key()));
        } else if (torrent.getDownloadUrl() != null) {
          torrentClient.addTorrent(infoHash, torrent.getDownloadUrl());
        }
        List<Integer> allIndices = torrent.getFiles().stream().map(TorrentFile::index).toList();
        torrentClient.disableAllFiles(infoHash, allIndices);
      }
      long fileSizeBytes = cf.getTorrent().getFiles().stream()
          .filter(f -> f.index() == cf.getFileIndex())
          .mapToLong(TorrentFile::sizeBytes)
          .findFirst()
          .orElse(0L);
      float progress = cf.getProgress() != null ? cf.getProgress() : 0f;
      long remainingBytes = (long) (fileSizeBytes * (1 - progress));
      long freeSpace = torrentClient.getFreeSpaceBytes();
      if (freeSpace - remainingBytes < minFreeSpaceBytes) {
        log.warn("Not enough disk space to enable file for cachedFile=[{}]: freeSpace={} remainingBytes={} minFree={}",
            cf.getId(), freeSpace, remainingBytes, minFreeSpaceBytes);
        cachedFileRepository.save(cf);
        return;
      }
      torrentClient.enableFile(infoHash, cf.getFileIndex());
      var fileProgress = torrentClient.getFileProgress(infoHash, cf.getFileIndex());
      cf.updateProgress(fileProgress.progress());
      if (fileProgress.progress() >= 1.0f) {
        String relativePath = torrentClient.getRelativeFilePath(infoHash, cf.getFileIndex());
        String fileUrl = downloadsBaseUrl + "/" + relativePath;
        String metadata = runFfprobe(fileUrl);
        cf.markDownloaded(relativePath, metadata);
        log.info("CachedFile [{}] downloaded, metadata={}", cf.getId(), metadata != null ? "captured" : "unavailable");
      }
    }
    cachedFileRepository.save(cf);
    if (cf.getStatus() != FileDownloadStatus.DOWNLOADING) {
      boolean anyActive = cachedFileRepository.existsByTorrentIdAndStatusIn(cf.getTorrent().getId(), ACTIVE_STATUSES);
      if (!anyActive) {
        torrentClient.removeTorrent(cf.getTorrent().getInfoHash(), true);
      }
    }
  }

  @Transactional
  public void pollDownloadedFile(String cachedFileIdStr) {
    Long cachedFileId = TSIDValidator.validate(cachedFileIdStr);
    CachedFile cf = cachedFileRepository.findById(cachedFileId).orElse(null);
    if (cf == null || cf.getStatus() != FileDownloadStatus.DOWNLOADED) return;
    if (cf.getDownloadedAt() != null && cf.getDownloadedAt().plus(downloadedFileTtl).isBefore(Instant.now())) {
      log.info("CachedFile [{}] DOWNLOADED TTL expired, marking done", cf.getId());
      long sizeBytes = cf.getTorrent().getFiles().stream()
          .filter(f -> f.index() == cf.getFileIndex())
          .mapToLong(TorrentFile::sizeBytes)
          .findFirst()
          .orElse(0L);
      cf.markDoneLocal(cf.getPath(), sizeBytes);
      cachedFileRepository.save(cf);
      boolean anyActive = cachedFileRepository.existsByTorrentIdAndStatusIn(cf.getTorrent().getId(), ACTIVE_STATUSES);
      if (!anyActive) {
        torrentClient.removeTorrent(cf.getTorrent().getInfoHash(), true);
      }
    }
  }

  private String runFfprobe(String absolutePath) {
    try {
      Process process = new ProcessBuilder(
          "ffprobe", "-v", "error", "-print_format", "json", "-show_format", "-show_streams", absolutePath)
          .redirectErrorStream(true)
          .start();
      String output = new String(process.getInputStream().readAllBytes());
      int exitCode = process.waitFor();
      if (exitCode != 0) {
        log.warn("ffprobe exited with code {} for path [{}]: {}", exitCode, absolutePath, output);
        return null;
      }
      return output;
    } catch (Exception e) {
      log.warn("ffprobe failed for path [{}]: {}", absolutePath, e.getMessage());
      return null;
    }
  }

  private TorrentDownload findTorrentDownload(String idStr, String accountIdStr) {
    return torrentDownloadRepository.findByIdAndAccount_Id(TSIDValidator.validate(idStr), TSIDValidator.validate(accountIdStr))
        .orElseThrow(ResourceNotFoundException::new);
  }

  private FileDownloadDTO toDTO(CachedFile cf) {
    String signedUrl = cf.getStatus() == FileDownloadStatus.DONE && cf.isStoredInS3()
        ? objectStorageClient.generateSignedUrl(s3Key(cf))
        : null;
    return FileDownloadDTO.from(cf, signedUrl);
  }

  private String s3Key(CachedFile cf) {
    return cf.getS3Key() != null ? cf.getS3Key() : cf.getPath();
  }
}
