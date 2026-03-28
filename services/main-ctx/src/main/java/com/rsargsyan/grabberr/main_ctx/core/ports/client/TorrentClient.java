package com.rsargsyan.grabberr.main_ctx.core.ports.client;

import com.rsargsyan.grabberr.main_ctx.core.domain.valueobject.FileProgress;
import com.rsargsyan.grabberr.main_ctx.core.domain.valueobject.TorrentFile;

import java.util.List;
import java.util.Optional;

public interface TorrentClient {
  void addTorrent(String infoHash, String downloadUrl);
  void addTorrent(String infoHash, byte[] torrentFileBytes);
  byte[] exportTorrent(String infoHash);
  Optional<List<TorrentFile>> getFiles(String infoHash);
  void disableAllFiles(String infoHash, List<Integer> fileIndices);
  void enableFile(String infoHash, int fileIndex);
  FileProgress getFileProgress(String infoHash, int fileIndex);
  String getRelativeFilePath(String infoHash, int fileIndex);
  void removeTorrent(String infoHash, boolean deleteFiles);
  long getFreeSpaceBytes();
  Optional<String> getTorrentState(String infoHash);
}
