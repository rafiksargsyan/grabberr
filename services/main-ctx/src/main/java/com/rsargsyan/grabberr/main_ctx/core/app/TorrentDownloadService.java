package com.rsargsyan.grabberr.main_ctx.core.app;

import com.rsargsyan.grabberr.main_ctx.core.Util;
import com.rsargsyan.grabberr.main_ctx.core.app.dto.TorrentDownloadDTO;
import java.util.List;
import com.rsargsyan.grabberr.main_ctx.core.domain.aggregate.Account;
import com.rsargsyan.grabberr.main_ctx.core.domain.aggregate.Torrent;
import com.rsargsyan.grabberr.main_ctx.core.domain.aggregate.TorrentDownload;
import com.rsargsyan.grabberr.main_ctx.core.domain.valueobject.TorrentStatus;
import com.rsargsyan.grabberr.main_ctx.core.exception.ResourceNotFoundException;
import com.rsargsyan.grabberr.main_ctx.core.ports.client.TorrentClient;
import com.rsargsyan.grabberr.main_ctx.core.ports.client.TorrentParser;
import com.rsargsyan.grabberr.main_ctx.core.ports.repository.AccountRepository;
import com.rsargsyan.grabberr.main_ctx.core.ports.repository.TorrentDownloadRepository;
import com.rsargsyan.grabberr.main_ctx.core.ports.repository.TorrentRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TorrentDownloadService {

  private final TorrentRepository torrentRepository;
  private final TorrentDownloadRepository torrentDownloadRepository;
  private final AccountRepository accountRepository;
  private final TorrentClient torrentClient;
  private final TorrentParser torrentParser;

  @Autowired
  public TorrentDownloadService(TorrentRepository torrentRepository,
                                TorrentDownloadRepository torrentDownloadRepository,
                                AccountRepository accountRepository,
                                TorrentClient torrentClient,
                                TorrentParser torrentParser) {
    this.torrentRepository = torrentRepository;
    this.torrentDownloadRepository = torrentDownloadRepository;
    this.accountRepository = accountRepository;
    this.torrentClient = torrentClient;
    this.torrentParser = torrentParser;
  }

  @Transactional
  public TorrentDownloadDTO submitByUrl(String downloadUrl, String accountIdStr) {
    String infoHash = torrentParser.parseInfoHash(downloadUrl);
    Torrent torrent = findOrCreateTorrent(infoHash, downloadUrl,
        () -> torrentClient.addTorrent(infoHash, downloadUrl));
    return createTorrentDownload(torrent, accountIdStr);
  }

  @Transactional
  public TorrentDownloadDTO submitByFile(byte[] torrentFileBytes, String accountIdStr) {
    String infoHash = torrentParser.parseInfoHash(torrentFileBytes);
    Torrent torrent = findOrCreateTorrent(infoHash, null,
        () -> torrentClient.addTorrent(infoHash, torrentFileBytes));
    return createTorrentDownload(torrent, accountIdStr);
  }

  public List<TorrentDownloadDTO> list(String accountIdStr) {
    Long accountId = Util.validateTSID(accountIdStr);
    return torrentDownloadRepository.findByAccount_IdOrderByCreatedAtDesc(accountId).stream()
        .map(TorrentDownloadDTO::from)
        .toList();
  }

  public TorrentDownloadDTO getStatus(String id, String accountIdStr) {
    Long torrentDownloadId = Util.validateTSID(id);
    Long accountId = Util.validateTSID(accountIdStr);
    return torrentDownloadRepository.findByIdAndAccount_Id(torrentDownloadId, accountId)
        .map(TorrentDownloadDTO::from)
        .orElseThrow(ResourceNotFoundException::new);
  }

  private Torrent findOrCreateTorrent(String infoHash, String downloadUrl, Runnable addToClient) {
    return torrentRepository.findByInfoHash(infoHash)
        .map(existing -> {
          if (existing.getStatus() == TorrentStatus.FETCHING_METADATA) {
            addToClient.run(); // re-add in case qBittorrent lost it (e.g. container restart)
          }
          return existing;
        })
        .orElseGet(() -> {
          addToClient.run();
          Torrent torrent = new Torrent(infoHash, downloadUrl);
          return torrentRepository.save(torrent);
        });
  }

  private TorrentDownloadDTO createTorrentDownload(Torrent torrent, String accountIdStr) {
    Long accountId = Util.validateTSID(accountIdStr);
    Account account = accountRepository.findById(accountId)
        .orElseThrow(ResourceNotFoundException::new);
    TorrentDownload torrentDownload = new TorrentDownload(account, torrent);
    torrentDownloadRepository.save(torrentDownload);
    return TorrentDownloadDTO.from(torrentDownload);
  }
}
