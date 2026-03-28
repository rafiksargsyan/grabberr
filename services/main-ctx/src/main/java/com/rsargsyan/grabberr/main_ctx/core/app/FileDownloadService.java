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

  @Value("${grabberr.download-timeout:PT24H}")
  private Duration downloadTimeout;

  @Value("${grabberr.min-free-space-bytes:10737418240}")
  private long minFreeSpaceBytes;

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

  public List<FileDownloadDTO> list(String torrentDownloadIdStr, String accountIdStr) {
    TorrentDownload torrentDownload = findTorrentDownload(torrentDownloadIdStr, accountIdStr);
    return cachedFileRepository.findByTorrentId(torrentDownload.getTorrent().getId()).stream()
        .map(this::toDTO)
        .toList();
  }

  public FileDownloadDTO getStatus(String torrentDownloadIdStr, int fileIndex, String accountIdStr) {
    TorrentDownload torrentDownload = findTorrentDownload(torrentDownloadIdStr, accountIdStr);
    return cachedFileRepository
        .findByTorrentIdAndFileIndex(torrentDownload.getTorrent().getId(), fileIndex)
        .map(this::toDTO)
        .orElseThrow(ResourceNotFoundException::new);
  }

  @Transactional
  public void cancelClaimsForAccount(Long torrentId, Long accountId) {
    cachedFileClaimRepository.deleteByAccount_IdAndCachedFile_TorrentId(accountId, torrentId);
    cachedFileRepository.findByTorrentId(torrentId).forEach(this::cancelIfNoClaims);
  }

  private void cancelIfNoClaims(CachedFile cf) {
    if (cf.getStatus() == FileDownloadStatus.DONE) return;
    if (!cachedFileClaimRepository.existsByCachedFile_Id(cf.getId())) {
      log.info("CachedFile [{}] has no remaining claims, deleting", cf.getId());
      cachedFileRepository.delete(cf);
      boolean anyActive = cachedFileRepository.existsByTorrentIdAndStatusIn(
          cf.getTorrent().getId(), List.of(FileDownloadStatus.SUBMITTED, FileDownloadStatus.DOWNLOADING, FileDownloadStatus.TRANSFERRING));
      if (!anyActive) {
        torrentClient.removeTorrent(cf.getTorrent().getInfoHash(), true);
      }
    }
  }

  public void pollFileDownloads() {
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
    if (cf.getCreatedAt().plus(downloadTimeout).isBefore(Instant.now())) {
      cf.markFailed();
    } else {
      objectStorageClient.getSize(cf.getPath())
          .ifPresent(size -> cf.markDone(cf.getPath(), size));
    }
    cachedFileRepository.save(cf);
    if (cf.getStatus() != FileDownloadStatus.TRANSFERRING) {
      boolean anyActive = cachedFileRepository.existsByTorrentIdAndStatusIn(
          cf.getTorrent().getId(), List.of(FileDownloadStatus.SUBMITTED, FileDownloadStatus.DOWNLOADING, FileDownloadStatus.TRANSFERRING));
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
        try {
          fileTransferClient.startTransfer(relativePath, relativePath);
          cf.markTransferring(relativePath);
        } catch (Exception e) {
          log.warn("startTransfer [{}]: failed, will retry next poll", relativePath, e);
        }
      }
    }
    cachedFileRepository.save(cf);
    if (cf.getStatus() != FileDownloadStatus.DOWNLOADING) {
      boolean anyActive = cachedFileRepository.existsByTorrentIdAndStatusIn(
          cf.getTorrent().getId(), List.of(FileDownloadStatus.SUBMITTED, FileDownloadStatus.DOWNLOADING, FileDownloadStatus.TRANSFERRING));
      if (!anyActive) {
        torrentClient.removeTorrent(cf.getTorrent().getInfoHash(), true);
      }
    }
  }

  private TorrentDownload findTorrentDownload(String idStr, String accountIdStr) {
    return torrentDownloadRepository.findByIdAndAccount_Id(TSIDValidator.validate(idStr), TSIDValidator.validate(accountIdStr))
        .orElseThrow(ResourceNotFoundException::new);
  }

  private FileDownloadDTO toDTO(CachedFile cf) {
    String signedUrl = cf.getStatus() == FileDownloadStatus.DONE
        ? objectStorageClient.generateSignedUrl(cf.getPath())
        : null;
    return FileDownloadDTO.from(cf, signedUrl);
  }
}
