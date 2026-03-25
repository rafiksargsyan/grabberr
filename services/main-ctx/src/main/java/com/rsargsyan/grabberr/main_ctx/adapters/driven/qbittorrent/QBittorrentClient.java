package com.rsargsyan.grabberr.main_ctx.adapters.driven.qbittorrent;

import com.rsargsyan.grabberr.main_ctx.core.domain.valueobject.TorrentFile;
import com.rsargsyan.grabberr.main_ctx.core.ports.client.TorrentClient;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

@Component
public class QBittorrentClient implements TorrentClient {

  private record QbtFile(
      int index,
      String name,
      long size,
      float progress
  ) {}

  private static final Logger log = LoggerFactory.getLogger(QBittorrentClient.class);

  @Value("${grabberr.qbittorrent.url}")
  private String baseUrl;

  @Value("${grabberr.qbittorrent.username}")
  private String username;

  @Value("${grabberr.qbittorrent.password}")
  private String password;

  private volatile String sid;
  private RestClient restClient;

  @PostConstruct
  public void init() {
    this.restClient = RestClient.builder()
        .baseUrl(baseUrl)
        .build();
  }

  @Override
  public void addTorrent(String infoHash, String downloadUrl) {
    String body = "urls=" + encode(downloadUrl) + "&savepath=/downloads";
    var response = postFormWithResponse("/api/v2/torrents/add", body);
    logAddResponse(infoHash, response);
  }

  @Override
  public void addTorrent(String infoHash, byte[] torrentFileBytes) {
    String boundary = "grabberr-boundary-" + infoHash;
    byte[] multipartBody = buildMultipartBody(infoHash, torrentFileBytes, boundary);
    String contentType = "multipart/form-data; boundary=" + boundary;

    var response = withSession(sid ->
        restClient.post()
            .uri("/api/v2/torrents/add")
            .header(HttpHeaders.COOKIE, "SID=" + sid)
            .header(HttpHeaders.CONTENT_TYPE, contentType)
            .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(multipartBody.length))
            .body(multipartBody)
            .retrieve()
            .toEntity(String.class)
    );
    logAddResponse(infoHash, response.getBody());
  }

  private static byte[] buildMultipartBody(String infoHash, byte[] fileBytes, String boundary) {
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] crlf = "\r\n".getBytes(StandardCharsets.UTF_8);
      byte[] dashdash = "--".getBytes(StandardCharsets.UTF_8);

      // file part
      out.write(dashdash); out.write(boundary.getBytes(StandardCharsets.UTF_8)); out.write(crlf);
      out.write(("Content-Disposition: form-data; name=\"torrents\"; filename=\"" + infoHash + ".torrent\"").getBytes(StandardCharsets.UTF_8)); out.write(crlf);
      out.write("Content-Type: application/x-bittorrent".getBytes(StandardCharsets.UTF_8)); out.write(crlf);
      out.write(crlf);
      out.write(fileBytes); out.write(crlf);

      // savepath part
      out.write(dashdash); out.write(boundary.getBytes(StandardCharsets.UTF_8)); out.write(crlf);
      out.write("Content-Disposition: form-data; name=\"savepath\"".getBytes(StandardCharsets.UTF_8)); out.write(crlf);
      out.write(crlf);
      out.write("/downloads".getBytes(StandardCharsets.UTF_8)); out.write(crlf);

      // closing boundary
      out.write(dashdash); out.write(boundary.getBytes(StandardCharsets.UTF_8)); out.write(dashdash); out.write(crlf);

      return out.toByteArray();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void logAddResponse(String infoHash, String response) {
    if ("Ok.".equals(response)) {
      log.info("addTorrent [{}]: added successfully", infoHash);
    } else if ("Fails.".equals(response)) {
      log.warn("addTorrent [{}]: qBittorrent returned Fails. (already exists or invalid)", infoHash);
    } else {
      log.warn("addTorrent [{}]: unexpected response: {}", infoHash, response);
    }
  }

  @Override
  public Optional<List<TorrentFile>> getFiles(String infoHash) {
    try {
      List<QbtFile> files = withSession(sid ->
          restClient.get()
              .uri("/api/v2/torrents/files?hash={hash}", infoHash)
              .header(HttpHeaders.COOKIE, "SID=" + sid)
              .retrieve()
              .body(new org.springframework.core.ParameterizedTypeReference<List<QbtFile>>() {})
      );
      log.info("getFiles [{}]: raw response has {} entries", infoHash, files == null ? "null" : files.size());
      if (files == null || files.isEmpty()) return Optional.empty();
      List<TorrentFile> result = files.stream()
          .map(f -> new TorrentFile(f.index(), f.name(), f.size()))
          .toList();
      return Optional.of(result);
    } catch (HttpClientErrorException e) {
      log.warn("getFiles [{}]: HTTP {} - returning empty", infoHash, e.getStatusCode());
      return Optional.empty();
    } catch (Exception e) {
      log.error("getFiles [{}]: unexpected error", infoHash, e);
      return Optional.empty();
    }
  }

  @Override
  public void enableFile(String infoHash, int fileIndex) {
    postForm("/api/v2/torrents/filePrio",
        "hash=" + infoHash + "&id=" + fileIndex + "&priority=1");
  }

  @Override
  public float getFileProgress(String infoHash, int fileIndex) {
    List<QbtFile> files = withSession(sid ->
        restClient.get()
            .uri("/api/v2/torrents/files?hash={hash}", infoHash)
            .header(HttpHeaders.COOKIE, "SID=" + sid)
            .retrieve()
            .body(new org.springframework.core.ParameterizedTypeReference<List<QbtFile>>() {})
    );
    if (files == null) return 0f;
    return files.stream()
        .filter(f -> f.index() == fileIndex)
        .map(QbtFile::progress)
        .findFirst()
        .orElse(0f);
  }

  @Override
  public String getRelativeFilePath(String infoHash, int fileIndex) {
    List<QbtFile> files = withSession(sid ->
        restClient.get()
            .uri("/api/v2/torrents/files?hash={hash}", infoHash)
            .header(HttpHeaders.COOKIE, "SID=" + sid)
            .retrieve()
            .body(new org.springframework.core.ParameterizedTypeReference<List<QbtFile>>() {})
    );
    if (files == null) throw new RuntimeException("Could not get files from qBittorrent");
    return files.stream()
        .filter(f -> f.index() == fileIndex)
        .map(QbtFile::name)
        .findFirst()
        .orElseThrow(() -> new RuntimeException("File index not found: " + fileIndex));
  }

  @Override
  public void removeTorrent(String infoHash, boolean deleteFiles) {
    postForm("/api/v2/torrents/delete",
        "hashes=" + infoHash + "&deleteFiles=" + deleteFiles);
  }

  private void postForm(String uri, String body) {
    postFormWithResponse(uri, body);
  }

  private String postFormWithResponse(String uri, String body) {
    var response = withSession(sid ->
        restClient.post()
            .uri(uri)
            .header(HttpHeaders.COOKIE, "SID=" + sid)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(body)
            .retrieve()
            .toEntity(String.class)
    );
    return response.getBody();
  }

  private <T> T withSession(java.util.function.Function<String, T> action) {
    String currentSid = ensureSid();
    var response = action.apply(currentSid);
    return response;
  }

  private synchronized String ensureSid() {
    if (sid != null) return sid;
    return login();
  }

  private String login() {
    String body = "username=" + encode(username) + "&password=" + encode(password);
    var response = restClient.post()
        .uri("/api/v2/auth/login")
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .body(body)
        .retrieve()
        .toEntity(String.class);

    log.info("qBittorrent login response body: [{}] headers: {}", response.getBody(), response.getHeaders());
    List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
    if (cookies != null) {
      for (String cookie : cookies) {
        if (cookie.startsWith("SID=")) {
          sid = cookie.split(";")[0].substring(4);
          log.info("qBittorrent login successful, SID obtained");
          return sid;
        }
      }
    }
    throw new RuntimeException("qBittorrent login failed — check credentials");
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
