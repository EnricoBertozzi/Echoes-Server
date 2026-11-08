package com.n0hana.echoes_server.device.exception;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class DeviceExceptionHandler {

  @ExceptionHandler(DeviceNotFoundException.class)
  public ResponseEntity<Map<String, String>> handleNotFound(DeviceNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", ex.getMessage()));
  }
}
