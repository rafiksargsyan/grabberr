package com.rsargsyan.grabberr.main_ctx.core.ports.client;

import com.rsargsyan.grabberr.main_ctx.core.domain.valueobject.TorrentFile;

import java.util.List;
import java.util.Optional;

public interface TorrentClient {
  void addTorrent(String infoHash, String downloadUrl);
  void addTorrent(String infoHash, byte[] torrentFileBytes);
  Optional<List<TorrentFile>> getFiles(String infoHash);
  void enableFile(String infoHash, int fileIndex);
  float getFileProgress(String infoHash, int fileIndex);
  String getRelativeFilePath(String infoHash, int fileIndex);
  void removeTorrent(String infoHash, boolean deleteFiles);
}
