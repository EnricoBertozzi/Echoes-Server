package com.n0hana.echoes_server.device.event;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class DeviceEventExceptionHandler {

  @ExceptionHandler(InvalidDeviceEventException.class)
  public ResponseEntity<Map<String, String>> handleInvalidEvent(InvalidDeviceEventException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", ex.getMessage()));
  }
}
