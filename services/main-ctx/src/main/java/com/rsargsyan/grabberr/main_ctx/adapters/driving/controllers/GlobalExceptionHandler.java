package com.rsargsyan.grabberr.main_ctx.adapters.driving.controllers;

import com.rsargsyan.grabberr.main_ctx.core.exception.AuthorizationException;
import com.rsargsyan.grabberr.main_ctx.core.exception.DomainException;
import com.rsargsyan.grabberr.main_ctx.core.exception.ResourceNotFoundException;
import com.rsargsyan.grabberr.main_ctx.core.exception.TorrentNotReadyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  public record ErrorResponse(String code, String message) {}

  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleNotFoundException(ResourceNotFoundException e) {
    return new ResponseEntity<>(new ErrorResponse(e.getClass().getSimpleName(), e.getMessage()),
        HttpStatus.NOT_FOUND);
  }

  @ExceptionHandler(TorrentNotReadyException.class)
  public ResponseEntity<ErrorResponse> handleTorrentNotReady(TorrentNotReadyException e) {
    return new ResponseEntity<>(new ErrorResponse(e.getClass().getSimpleName(), e.getMessage()),
        HttpStatus.CONFLICT);
  }

  @ExceptionHandler(AuthorizationException.class)
  public ResponseEntity<ErrorResponse> handleAuthorizationException(AuthorizationException e) {
    return new ResponseEntity<>(new ErrorResponse(e.getClass().getSimpleName(), e.getMessage()),
        HttpStatus.FORBIDDEN);
  }

  @ExceptionHandler(DomainException.class)
  public ResponseEntity<ErrorResponse> handleDomainException(DomainException e) {
    return new ResponseEntity<>(new ErrorResponse(e.getClass().getSimpleName(), e.getMessage()),
        HttpStatus.BAD_REQUEST);
  }
}
