package com.rsargsyan.grabberr.main_ctx.core;

import io.hypersistence.tsid.TSID;
import org.apache.commons.codec.binary.Base32;

import java.io.IOException;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;

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
    URI uri = URI.create(magnet);
    String query = uri.getRawSchemeSpecificPart();
    for (String param : query.substring(1).split("&")) {
      if (param.startsWith("xt=urn:btih:")) {
        String hash = param.substring("xt=urn:btih:".length());
        if (hash.length() == 40) return hash.toLowerCase();
        if (hash.length() == 32) return HexFormat.of().formatHex(new Base32().decode(hash.toUpperCase()));
        throw new IllegalArgumentException("Invalid btih hash length in magnet link");
      }
    }
    throw new IllegalArgumentException("No xt=urn:btih parameter found in magnet link");
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
