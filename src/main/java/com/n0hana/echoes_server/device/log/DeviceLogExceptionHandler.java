package com.n0hana.echoes_server.device.log;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class DeviceLogExceptionHandler {

  @ExceptionHandler(TooManyDeviceLogsException.class)
  public ResponseEntity<Map<String, String>> handleTooMany(TooManyDeviceLogsException ex) {
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("message", ex.getMessage()));
  }
}
