package com.n0hana.echoes_server.term.exception;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class TermExceptionHandler {

    @ExceptionHandler(TermNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(TermNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(TermVersionException.class)
    public ResponseEntity<Map<String, String>> handleVersion(TermVersionException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }
}
