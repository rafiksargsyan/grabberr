package com.rsargsyan.grabberr.main_ctx.core.ports.client;

public interface TorrentParser {
  String parseInfoHash(String downloadUrl);   // magnet link or .torrent file URL
  String parseInfoHash(byte[] torrentFile);   // uploaded .torrent file bytes
}
