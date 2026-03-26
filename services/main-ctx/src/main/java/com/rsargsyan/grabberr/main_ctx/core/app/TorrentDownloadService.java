package com.rsargsyan.grabberr.main_ctx.core.app;

import com.rsargsyan.grabberr.main_ctx.core.Util;
import com.rsargsyan.grabberr.main_ctx.core.app.dto.TorrentDownloadDTO;
import com.rsargsyan.grabberr.main_ctx.core.domain.aggregate.Account;
import com.rsargsyan.grabberr.main_ctx.core.domain.aggregate.Torrent;
import com.rsargsyan.grabberr.main_ctx.core.domain.aggregate.TorrentDownload;
import com.rsargsyan.grabberr.main_ctx.core.domain.service.TSIDValidator;
import com.rsargsyan.grabberr.main_ctx.core.domain.valueobject.TorrentStatus;
import com.rsargsyan.grabberr.main_ctx.core.exception.InvalidDownloadUrlException;
import com.rsargsyan.grabberr.main_ctx.core.exception.InvalidTorrentFileException;
import com.rsargsyan.grabberr.main_ctx.core.exception.ResourceNotFoundException;
import com.rsargsyan.grabberr.main_ctx.core.ports.client.ObjectStorageClient;
import com.rsargsyan.grabberr.main_ctx.core.ports.client.TorrentClient;
import com.rsargsyan.grabberr.main_ctx.core.ports.repository.AccountRepository;
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

@Slf4j
@Service
public class TorrentDownloadService {

  @Value("${grabberr.metadata-timeout:PT2H}")
  private Duration metadataTimeout;

  private final TorrentRepository torrentRepository;
  private final TorrentDownloadRepository torrentDownloadRepository;
  private final AccountRepository accountRepository;
  private final TorrentClient torrentClient;
  private final ObjectStorageClient objectStorageClient;

  @Lazy
  @Autowired
  private TorrentDownloadService self;

  @Autowired
  public TorrentDownloadService(TorrentRepository torrentRepository,
                                TorrentDownloadRepository torrentDownloadRepository,
                                AccountRepository accountRepository,
                                TorrentClient torrentClient,
                                ObjectStorageClient objectStorageClient) {
    this.torrentRepository = torrentRepository;
    this.torrentDownloadRepository = torrentDownloadRepository;
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
    try {
      infoHash = Util.parseInfoHash(torrentFileBytes);
    } catch (IllegalArgumentException e) {
      throw new InvalidTorrentFileException();
    }
    Torrent torrent = findOrCreateTorrent(infoHash, null,
        () -> torrentClient.addTorrent(infoHash, torrentFileBytes));
    if (torrent.getTorrentS3Key() == null) {
      String s3Key = torrentS3Key(infoHash);
      objectStorageClient.upload(s3Key, torrentFileBytes, "application/x-bittorrent");
      torrent.setTorrentS3Key(s3Key);
      torrentRepository.save(torrent);
    }
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

  public void pollMetadata() {
    List<Long> ids = torrentRepository.findIdsByStatus(TorrentStatus.FETCHING_METADATA);
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
    if (torrent == null || torrent.getStatus() != TorrentStatus.FETCHING_METADATA) return;
    log.info("Polling torrent [{}]", torrent.getInfoHash());
    if (torrent.getCreatedAt().plus(metadataTimeout).isBefore(Instant.now())) {
      log.warn("Torrent [{}] timed out, marking failed", torrent.getInfoHash());
      torrent.markFailed();
      torrentClient.removeTorrent(torrent.getInfoHash(), false);
    } else {
      var files = torrentClient.getFiles(torrent.getInfoHash());
      log.info("Torrent [{}] getFiles result: {}", torrent.getInfoHash(), files);
      files.ifPresent(fileList -> {
        List<Integer> indices = fileList.stream().map(f -> f.index()).toList();
        try {
          torrentClient.disableAllFiles(torrent.getInfoHash(), indices);
        } catch (Exception e) {
          log.warn("disableAllFiles [{}]: failed, continuing", torrent.getInfoHash(), e);
        }
        torrent.markReady(fileList);
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
      });
    }
    torrentRepository.save(torrent);
  }

  private Torrent findOrCreateTorrent(String infoHash, String downloadUrl, Runnable addToClient) {
    return torrentRepository.findByInfoHash(infoHash)
        .map(existing -> {
          if (existing.getStatus() == TorrentStatus.FETCHING_METADATA) {
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
