package com.rsargsyan.grabberr.main_ctx.core.app;

import com.rsargsyan.grabberr.main_ctx.core.Util;
import com.rsargsyan.grabberr.main_ctx.core.app.dto.FileDownloadDTO;
import com.rsargsyan.grabberr.main_ctx.core.domain.aggregate.CachedFile;
import com.rsargsyan.grabberr.main_ctx.core.domain.aggregate.FileDownload;
import com.rsargsyan.grabberr.main_ctx.core.domain.aggregate.TorrentDownload;
import com.rsargsyan.grabberr.main_ctx.core.domain.valueobject.FileDownloadStatus;
import com.rsargsyan.grabberr.main_ctx.core.domain.valueobject.TorrentStatus;
import com.rsargsyan.grabberr.main_ctx.core.exception.InvalidIdException;
import com.rsargsyan.grabberr.main_ctx.core.exception.ResourceNotFoundException;
import com.rsargsyan.grabberr.main_ctx.core.exception.TorrentNotReadyException;
import com.rsargsyan.grabberr.main_ctx.core.ports.client.ObjectStorageClient;
import com.rsargsyan.grabberr.main_ctx.core.ports.client.TorrentClient;
import com.rsargsyan.grabberr.main_ctx.core.ports.repository.CachedFileRepository;
import com.rsargsyan.grabberr.main_ctx.core.ports.repository.FileDownloadRepository;
import com.rsargsyan.grabberr.main_ctx.core.ports.repository.TorrentDownloadRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class FileDownloadService {

  private final TorrentDownloadRepository torrentDownloadRepository;
  private final CachedFileRepository cachedFileRepository;
  private final FileDownloadRepository fileDownloadRepository;
  private final TorrentClient torrentClient;
  private final ObjectStorageClient objectStorageClient;

  @Autowired
  public FileDownloadService(TorrentDownloadRepository torrentDownloadRepository,
                             CachedFileRepository cachedFileRepository,
                             FileDownloadRepository fileDownloadRepository,
                             TorrentClient torrentClient,
                             ObjectStorageClient objectStorageClient) {
    this.torrentDownloadRepository = torrentDownloadRepository;
    this.cachedFileRepository = cachedFileRepository;
    this.fileDownloadRepository = fileDownloadRepository;
    this.torrentClient = torrentClient;
    this.objectStorageClient = objectStorageClient;
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
      throw new InvalidIdException();
    }

    return fileDownloadRepository
        .findByTorrentDownloadIdAndFileIndex(torrentDownload.getId(), fileIndex)
        .map(fd -> toDTO(fd))
        .orElseGet(() -> {
          CachedFile cachedFile = findOrCreateCachedFile(torrentDownload, fileIndex);
          FileDownload fd = new FileDownload(torrentDownload, fileIndex, cachedFile);
          fileDownloadRepository.save(fd);
          return toDTO(fd);
        });
  }

  public FileDownloadDTO getStatus(String torrentDownloadIdStr, int fileIndex, String accountIdStr) {
    TorrentDownload torrentDownload = findTorrentDownload(torrentDownloadIdStr, accountIdStr);
    return fileDownloadRepository
        .findByTorrentDownloadIdAndFileIndex(torrentDownload.getId(), fileIndex)
        .map(fd -> toDTO(fd))
        .orElseThrow(ResourceNotFoundException::new);
  }

  private TorrentDownload findTorrentDownload(String idStr, String accountIdStr) {
    Long id = Util.validateTSID(idStr);
    Long accountId = Util.validateTSID(accountIdStr);
    return torrentDownloadRepository.findByIdAndAccount_Id(id, accountId)
        .orElseThrow(ResourceNotFoundException::new);
  }

  private CachedFile findOrCreateCachedFile(TorrentDownload torrentDownload, int fileIndex) {
    return cachedFileRepository
        .findByTorrentIdAndFileIndex(torrentDownload.getTorrent().getId(), fileIndex)
        .orElseGet(() -> {
          torrentClient.enableFile(torrentDownload.getTorrent().getInfoHash(), fileIndex);
          CachedFile cachedFile = new CachedFile(torrentDownload.getTorrent(), fileIndex);
          return cachedFileRepository.save(cachedFile);
        });
  }

  private FileDownloadDTO toDTO(FileDownload fd) {
    String signedUrl = fd.getCachedFile().getStatus() == FileDownloadStatus.DONE
        ? objectStorageClient.generateSignedUrl(fd.getCachedFile().getPath())
        : null;
    return FileDownloadDTO.from(fd, signedUrl);
  }
}
