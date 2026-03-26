package com.rsargsyan.grabberr.main_ctx.adapters.driving.controllers;

import com.rsargsyan.grabberr.main_ctx.core.app.FileDownloadService;
import com.rsargsyan.grabberr.main_ctx.core.app.TorrentDownloadService;
import com.rsargsyan.grabberr.main_ctx.core.app.dto.FileDownloadDTO;
import com.rsargsyan.grabberr.main_ctx.core.app.dto.TorrentDownloadDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/torrent-download")
public class TorrentDownloadController {

  private final TorrentDownloadService torrentDownloadService;
  private final FileDownloadService fileDownloadService;

  @Autowired
  public TorrentDownloadController(TorrentDownloadService torrentDownloadService,
                                   FileDownloadService fileDownloadService) {
    this.torrentDownloadService = torrentDownloadService;
    this.fileDownloadService = fileDownloadService;
  }

  @GetMapping
  public ResponseEntity<List<TorrentDownloadDTO>> list() {
    String accountId = UserContextHolder.get().getAccountId();
    return ResponseEntity.ok(torrentDownloadService.list(accountId));
  }

  @PostMapping
  public ResponseEntity<TorrentDownloadDTO> submitByUrl(@RequestParam String downloadUrl) {
    String accountId = UserContextHolder.get().getAccountId();
    return new ResponseEntity<>(torrentDownloadService.submitByUrl(downloadUrl, accountId), HttpStatus.CREATED);
  }

  @PostMapping("/upload")
  public ResponseEntity<TorrentDownloadDTO> submitByFile(@RequestParam MultipartFile file)
      throws IOException {
    String accountId = UserContextHolder.get().getAccountId();
    return new ResponseEntity<>(torrentDownloadService.submitByFile(file.getBytes(), accountId), HttpStatus.CREATED);
  }

  @GetMapping("/{id}")
  public ResponseEntity<TorrentDownloadDTO> getStatus(@PathVariable String id) {
    String accountId = UserContextHolder.get().getAccountId();
    return ResponseEntity.ok(torrentDownloadService.getStatus(id, accountId));
  }

  @GetMapping("/{id}/file")
  public ResponseEntity<List<FileDownloadDTO>> listFileDownloads(@PathVariable String id) {
    String accountId = UserContextHolder.get().getAccountId();
    return ResponseEntity.ok(fileDownloadService.list(id, accountId));
  }

  @PutMapping("/{id}/file/{fileIndex}")
  public ResponseEntity<FileDownloadDTO> claimFile(@PathVariable String id,
                                                   @PathVariable int fileIndex) {
    String accountId = UserContextHolder.get().getAccountId();
    return ResponseEntity.ok(fileDownloadService.claim(id, fileIndex, accountId));
  }

  @GetMapping("/{id}/file/{fileIndex}")
  public ResponseEntity<FileDownloadDTO> getFileStatus(@PathVariable String id,
                                                       @PathVariable int fileIndex) {
    String accountId = UserContextHolder.get().getAccountId();
    return ResponseEntity.ok(fileDownloadService.getStatus(id, fileIndex, accountId));
  }
}
