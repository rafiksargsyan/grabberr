package com.rsargsyan.grabberr.main_ctx.core.app;

import com.rsargsyan.grabberr.main_ctx.core.Util;
import com.rsargsyan.grabberr.main_ctx.core.app.dto.TorrentDownloadDTO;
import com.rsargsyan.grabberr.main_ctx.core.domain.aggregate.Account;
import com.rsargsyan.grabberr.main_ctx.core.domain.aggregate.Torrent;
import com.rsargsyan.grabberr.main_ctx.core.domain.aggregate.TorrentDownload;
import com.rsargsyan.grabberr.main_ctx.core.domain.service.TSIDValidator;
import com.rsargsyan.grabberr.main_ctx.core.domain.valueobject.TorrentFile;
import com.rsargsyan.grabberr.main_ctx.core.domain.valueobject.TorrentStatus;
import com.rsargsyan.grabberr.main_ctx.core.exception.InvalidDownloadUrlException;
import com.rsargsyan.grabberr.main_ctx.core.exception.InvalidTorrentFileException;
import com.rsargsyan.grabberr.main_ctx.core.exception.ResourceNotFoundException;
import com.rsargsyan.grabberr.main_ctx.core.ports.client.ObjectStorageClient;
import com.rsargsyan.grabberr.main_ctx.core.ports.client.TorrentClient;
import com.rsargsyan.grabberr.main_ctx.core.ports.repository.AccountRepository;
import com.rsargsyan.grabberr.main_ctx.core.ports.repository.CachedFileRepository;
import com.rsargsyan.grabberr.main_ctx.core.ports.repository.TorrentDownloadRepository;
import com.rsargsyan.grabberr.main_ctx.core.ports.repository.TorrentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class TorrentDownloadService {

  @Value("${grabberr.queued-timeout:PT2H}")
  private Duration queuedTimeout;

  @Value("${grabberr.fetching-metadata-timeout:PT5M}")
  private Duration fetchingMetadataTimeout;

  private final TorrentRepository torrentRepository;
  private final TorrentDownloadRepository torrentDownloadRepository;
  private final CachedFileRepository cachedFileRepository;
  private final AccountRepository accountRepository;
  private final TorrentClient torrentClient;
  private final ObjectStorageClient objectStorageClient;

  @Lazy
  @Autowired
  private TorrentDownloadService self;

  @Lazy
  @Autowired
  private FileDownloadService fileDownloadService;

  @Autowired
  public TorrentDownloadService(TorrentRepository torrentRepository,
                                TorrentDownloadRepository torrentDownloadRepository,
                                CachedFileRepository cachedFileRepository,
                                AccountRepository accountRepository,
                                TorrentClient torrentClient,
                                ObjectStorageClient objectStorageClient) {
    this.torrentRepository = torrentRepository;
    this.torrentDownloadRepository = torrentDownloadRepository;
    this.cachedFileRepository = cachedFileRepository;
    this.accountRepository = accountRepository;
    this.torrentClient = torrentClient;
    this.objectStorageClient = objectStorageClient;
  }

  @Transactional
  public TorrentDownloadDTO submitByUrl(String downloadUrl, String accountIdStr) {
    String infoHash;
    try {
      infoHash = Util.parseInfoHash(downloadUrl);
    } catch (IllegalArgumentException e) {
      throw new InvalidDownloadUrlException();
    }
    Torrent torrent = findOrCreateTorrent(infoHash, downloadUrl,
        () -> torrentClient.addTorrent(infoHash, downloadUrl));
    return createTorrentDownload(torrent, accountIdStr);
  }

  @Transactional
  public TorrentDownloadDTO submitByFile(byte[] torrentFileBytes, String accountIdStr) {
    String infoHash;
    List<TorrentFile> files;
    try {
      infoHash = Util.parseInfoHash(torrentFileBytes);
      files = Util.parseTorrentFiles(torrentFileBytes);
    } catch (RuntimeException e) {
      log.warn("Failed to parse torrent file: {}", e.getMessage());
      throw new InvalidTorrentFileException();
    }
    Torrent torrent = findOrCreateTorrent(infoHash, null,
        () -> torrentClient.addTorrent(infoHash, torrentFileBytes));
    if (torrent.getStatus() == TorrentStatus.QUEUED || torrent.getStatus() == TorrentStatus.FAILED) {
      // Metadata is in the file — no need to poll qBittorrent for it
      try {
        torrentClient.disableAllFiles(infoHash, files.stream().map(f -> f.index()).toList());
      } catch (Exception e) {
        log.warn("disableAllFiles [{}]: failed, continuing", infoHash, e);
      }
      torrent.markReady(files);
      log.info("Torrent [{}] marked READY from file upload ({} file(s))", infoHash, files.size());
    }
    if (torrent.getTorrentS3Key() == null) {
      String s3Key = torrentS3Key(infoHash);
      objectStorageClient.upload(s3Key, torrentFileBytes, "application/x-bittorrent");
      torrent.setTorrentS3Key(s3Key);
    }
    torrentRepository.save(torrent);
    return createTorrentDownload(torrent, accountIdStr);
  }

  public List<TorrentDownloadDTO> list(String accountIdStr) {
    return torrentDownloadRepository.findByAccount_IdOrderByCreatedAtDesc(TSIDValidator.validate(accountIdStr)).stream()
        .map(TorrentDownloadDTO::from)
        .toList();
  }

  public TorrentDownloadDTO getStatus(String id, String accountIdStr) {
    return torrentDownloadRepository.findByIdAndAccount_Id(TSIDValidator.validate(id), TSIDValidator.validate(accountIdStr))
        .map(TorrentDownloadDTO::from)
        .orElseThrow(ResourceNotFoundException::new);
  }

  public TorrentDownloadDTO getStatusByInfoHash(String infoHash, String accountIdStr) {
    return torrentDownloadRepository.findByTorrent_InfoHashAndAccount_Id(infoHash.toLowerCase(), TSIDValidator.validate(accountIdStr))
        .map(TorrentDownloadDTO::from)
        .orElseThrow(ResourceNotFoundException::new);
  }

  @Transactional
  public void delete(String torrentDownloadIdStr, String accountIdStr) {
    TorrentDownload torrentDownload = torrentDownloadRepository
        .findByIdAndAccount_Id(TSIDValidator.validate(torrentDownloadIdStr), TSIDValidator.validate(accountIdStr))
        .orElseThrow(ResourceNotFoundException::new);

    Torrent torrent = torrentDownload.getTorrent();

    boolean isLast = !torrentDownloadRepository.existsByTorrent_IdAndIdNot(torrent.getId(), torrentDownload.getId());
    torrentDownloadRepository.delete(torrentDownload);
    torrentDownloadRepository.flush();

    if (isLast) {
      log.info("Last TorrentDownload removed for torrent [{}], cleaning up", torrent.getInfoHash());
      fileDownloadService.deleteAllForTorrent(torrent.getId());
      torrentClient.removeTorrent(torrent.getInfoHash(), true);
      if (torrent.getTorrentS3Key() != null) {
        try {
          objectStorageClient.delete(torrent.getTorrentS3Key());
        } catch (Exception e) {
          log.warn("Failed to delete .torrent file from S3 [{}]", torrent.getTorrentS3Key(), e);
        }
      }
      torrentRepository.deleteByIdDirect(torrent.getId());
    } else {
      fileDownloadService.cancelClaimsForAccount(torrent.getId(), TSIDValidator.validate(accountIdStr));
    }
  }

  public void pollMetadata() {
    List<Long> ids = torrentRepository.findIdsByStatusIn(
        List.of(TorrentStatus.QUEUED, TorrentStatus.FETCHING_METADATA));
    log.info("MetadataPolling: {} torrent(s) pending", ids.size());
    for (Long id : ids) {
      String strId = Util.tsidToString(id);
      try {
        self.processTorrentMetadata(strId);
      } catch (Exception e) {
        log.error("Failed to process torrent metadata for id [{}]", strId, e);
      }
    }
  }

  @Transactional
  public void processTorrentMetadata(String torrentIdStr) {
    Torrent torrent = torrentRepository.findById(TSIDValidator.validate(torrentIdStr)).orElse(null);
    if (torrent == null) return;
    if (torrent.getStatus() != TorrentStatus.QUEUED && torrent.getStatus() != TorrentStatus.FETCHING_METADATA) return;

    log.info("Polling torrent [{}] status={}", torrent.getInfoHash(), torrent.getStatus());

    Duration timeout = torrent.getStatus() == TorrentStatus.QUEUED ? queuedTimeout : fetchingMetadataTimeout;
    if (torrent.getCreatedAt().plus(timeout).isBefore(Instant.now())) {
      log.warn("Torrent [{}] timed out in {} state, marking failed", torrent.getInfoHash(), torrent.getStatus());
      torrent.markFailed();
      torrentClient.removeTorrent(torrent.getInfoHash(), false);
      torrentRepository.save(torrent);
      return;
    }

    Optional<String> qbtState = torrentClient.getTorrentState(torrent.getInfoHash());
    log.info("Torrent [{}] qBittorrent state: {}", torrent.getInfoHash(), qbtState.orElse("not found"));

    if (qbtState.isEmpty()) {
      // Not found in qBittorrent — re-add
      log.warn("Torrent [{}] not found in qBittorrent, re-adding", torrent.getInfoHash());
      try {
        if (torrent.getTorrentS3Key() != null) {
          torrentClient.addTorrent(torrent.getInfoHash(), objectStorageClient.download(torrent.getTorrentS3Key()));
        } else if (torrent.getDownloadUrl() != null) {
          torrentClient.addTorrent(torrent.getInfoHash(), torrent.getDownloadUrl());
        } else {
          log.error("Torrent [{}] has no download URL or S3 key, cannot re-add", torrent.getInfoHash());
        }
      } catch (Exception e) {
        log.error("Failed to re-add torrent [{}] to qBittorrent", torrent.getInfoHash(), e);
      }
      torrentRepository.save(torrent);
      return;
    }

    String state = qbtState.get();

    if ("metaDL".equals(state)) {
      if (torrent.getStatus() == TorrentStatus.QUEUED) {
        log.info("Torrent [{}] started fetching metadata", torrent.getInfoHash());
        torrent.markFetchingMetadata();
      }
      torrentRepository.save(torrent);
      return;
    }

    // Any other active state — try to get files (covers torrent files that are instantly ready)
    var files = torrentClient.getFiles(torrent.getInfoHash());
    if (files.isPresent() && !files.get().isEmpty()) {
      List<Integer> indices = files.get().stream().map(f -> f.index()).toList();
      try {
        torrentClient.disableAllFiles(torrent.getInfoHash(), indices);
      } catch (Exception e) {
        log.warn("disableAllFiles [{}]: failed, continuing", torrent.getInfoHash(), e);
      }
      torrent.markReady(files.get());
      if (torrent.getTorrentS3Key() == null && torrent.getDownloadUrl() != null
          && !torrent.getDownloadUrl().startsWith("magnet:")) {
        try {
          byte[] torrentBytes = URI.create(torrent.getDownloadUrl()).toURL().openStream().readAllBytes();
          String s3Key = torrentS3Key(torrent.getInfoHash());
          objectStorageClient.upload(s3Key, torrentBytes, "application/x-bittorrent");
          torrent.setTorrentS3Key(s3Key);
        } catch (Exception e) {
          log.warn("Failed to fetch/upload torrent [{}] to S3, will retry next poll", torrent.getInfoHash(), e);
        }
      }
    } else {
      log.debug("Torrent [{}] state={} no files yet, waiting", torrent.getInfoHash(), state);
    }

    torrentRepository.save(torrent);
  }

  private Torrent findOrCreateTorrent(String infoHash, String downloadUrl, Runnable addToClient) {
    return torrentRepository.findByInfoHash(infoHash)
        .map(existing -> {
          if (existing.getStatus() == TorrentStatus.FETCHING_METADATA
              || existing.getStatus() == TorrentStatus.FAILED) {
            addToClient.run();
          }
          return existing;
        })
        .orElseGet(() -> {
          addToClient.run();
          Torrent torrent = new Torrent(infoHash, downloadUrl);
          return torrentRepository.save(torrent);
        });
  }

  private static String torrentS3Key(String infoHash) {
    return "torrents/" + infoHash + ".torrent";
  }

  private TorrentDownloadDTO createTorrentDownload(Torrent torrent, String accountIdStr) {
    Account account = accountRepository.findById(TSIDValidator.validate(accountIdStr))
        .orElseThrow(ResourceNotFoundException::new);
    TorrentDownload torrentDownload = new TorrentDownload(account, torrent);
    torrentDownloadRepository.save(torrentDownload);
    return TorrentDownloadDTO.from(torrentDownload);
  }
}
