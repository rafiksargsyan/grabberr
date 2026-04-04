package com.rsargsyan.grabberr.main_ctx.core;

import com.rsargsyan.grabberr.main_ctx.core.domain.valueobject.TorrentFile;
import io.hypersistence.tsid.TSID;
import org.apache.commons.codec.binary.Base32;

import java.io.IOException;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

public class Util {

  public static String tsidToString(Long id) {
    return TSID.from(id).toString();
  }

  public static String parseInfoHash(String downloadUrl) {
    if (downloadUrl == null || downloadUrl.isBlank()) throw new IllegalArgumentException("Download URL is blank");
    if (downloadUrl.startsWith("magnet:")) {
      return parseMagnetInfoHash(downloadUrl);
    }
    try {
      byte[] bytes = URI.create(downloadUrl).toURL().openStream().readAllBytes();
      return parseInfoHash(bytes);
    } catch (IOException e) {
      throw new IllegalArgumentException("Failed to fetch torrent file from URL", e);
    }
  }

  public static String parseInfoHash(byte[] torrentFileBytes) {
    try {
      byte[] infoBytes = extractInfoDictBytes(torrentFileBytes);
      MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
      return HexFormat.of().formatHex(sha1.digest(infoBytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String parseMagnetInfoHash(String magnet) {
    int queryStart = magnet.indexOf('?');
    if (queryStart < 0) throw new IllegalArgumentException("No query string in magnet link");
    for (String param : magnet.substring(queryStart + 1).split("&")) {
      if (param.startsWith("xt=urn:btih:")) {
        String hash = param.substring("xt=urn:btih:".length());
        if (hash.length() == 40) return hash.toLowerCase();
        if (hash.length() == 32) return HexFormat.of().formatHex(new Base32().decode(hash.toUpperCase()));
        throw new IllegalArgumentException("Invalid btih hash length in magnet link");
      }
    }
    throw new IllegalArgumentException("No xt=urn:btih parameter found in magnet link");
  }

  /**
   * Parses the file list from a .torrent file's bencoded info dictionary.
   * Handles both single-file torrents (no "files" key) and multi-file torrents.
   */
  public static List<TorrentFile> parseTorrentFiles(byte[] torrentFileBytes) {
    byte[] infoBytes = extractInfoDictBytes(torrentFileBytes);
    // parse info dict keys
    if (infoBytes[0] != 'd') throw new IllegalArgumentException("Info dict is not a bencoded dict");
    int pos = 1;
    String name = null;
    Long length = null;
    List<TorrentFile> multiFiles = null;

    while (pos < infoBytes.length && infoBytes[pos] != 'e') {
      int[] keyResult = readString(infoBytes, pos);
      String key = new String(infoBytes, keyResult[0], keyResult[1] - keyResult[0]);
      int valStart = keyResult[1];

      if ("name".equals(key)) {
        int[] nameResult = readString(infoBytes, valStart);
        name = new String(infoBytes, nameResult[0], nameResult[1] - nameResult[0]);
        pos = nameResult[1];
      } else if ("length".equals(key)) {
        // single-file torrent
        int end = findByte(infoBytes, (byte) 'e', valStart + 1);
        length = Long.parseLong(new String(infoBytes, valStart + 1, end - valStart - 1));
        pos = end + 1;
      } else if ("files".equals(key)) {
        // multi-file torrent: list of dicts
        multiFiles = new ArrayList<>();
        pos = valStart + 1; // skip 'l'
        int fileIndex = 0;
        while (pos < infoBytes.length && infoBytes[pos] != 'e') {
          // each entry is a dict with "length" and "path"
          pos++; // skip 'd'
          long fileLength = 0;
          String filePath = null;
          while (pos < infoBytes.length && infoBytes[pos] != 'e') {
            int[] fKeyResult = readString(infoBytes, pos);
            String fKey = new String(infoBytes, fKeyResult[0], fKeyResult[1] - fKeyResult[0]);
            int fValStart = fKeyResult[1];
            if ("length".equals(fKey)) {
              int end = findByte(infoBytes, (byte) 'e', fValStart + 1);
              fileLength = Long.parseLong(new String(infoBytes, fValStart + 1, end - fValStart - 1));
              pos = end + 1;
            } else if ("path".equals(fKey)) {
              // list of path components
              pos = fValStart + 1; // skip 'l'
              StringBuilder sb = new StringBuilder();
              while (pos < infoBytes.length && infoBytes[pos] != 'e') {
                int[] part = readString(infoBytes, pos);
                if (!sb.isEmpty()) sb.append('/');
                sb.append(new String(infoBytes, part[0], part[1] - part[0]));
                pos = part[1];
              }
              pos++; // skip 'e' of path list
              filePath = sb.toString();
            } else {
              pos = skipValue(infoBytes, fValStart);
            }
          }
          pos++; // skip 'e' of file dict
          multiFiles.add(new TorrentFile(fileIndex++, filePath != null ? filePath : "unknown", fileLength));
        }
        pos++; // skip 'e' of files list
      } else {
        pos = skipValue(infoBytes, valStart);
      }
    }

    if (multiFiles != null) {
      return multiFiles;
    }
    // single-file torrent
    return List.of(new TorrentFile(0, name != null ? name : "unknown", length != null ? length : 0));
  }

  private static byte[] extractInfoDictBytes(byte[] data) {
    if (data[0] != 'd') throw new IllegalArgumentException("Not a valid torrent file (expected bencoded dict)");
    int pos = 1;
    while (pos < data.length && data[pos] != 'e') {
      int[] keyResult = readString(data, pos);
      String key = new String(data, keyResult[0], keyResult[1] - keyResult[0]);
      int valStart = keyResult[1];
      int valEnd = skipValue(data, valStart);
      if ("info".equals(key)) {
        return Arrays.copyOfRange(data, valStart, valEnd);
      }
      pos = valEnd;
    }
    throw new IllegalArgumentException("No info dictionary found in torrent file");
  }

  private static int skipValue(byte[] data, int pos) {
    byte b = data[pos];
    if (b == 'd' || b == 'l') {
      pos++;
      while (data[pos] != 'e') pos = skipValue(data, pos);
      return pos + 1;
    } else if (b == 'i') {
      return findByte(data, (byte) 'e', pos + 1) + 1;
    } else {
      int[] s = readString(data, pos);
      return s[1];
    }
  }

  private static int[] readString(byte[] data, int pos) {
    int sep = findByte(data, (byte) ':', pos);
    int len = Integer.parseInt(new String(data, pos, sep - pos));
    int start = sep + 1;
    return new int[]{start, start + len};
  }

  private static int findByte(byte[] data, byte target, int from) {
    for (int i = from; i < data.length; i++) {
      if (data[i] == target) return i;
    }
    throw new IllegalArgumentException("Unexpected end of torrent data while looking for byte: " + (char) target);
  }
}
