package com.n0hana.echoes_server.auscultation.exception;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AuscultationExceptionHandler {

    @ExceptionHandler(AuscultationPointNotFound.class)
    public ResponseEntity<Map<String, String>> handleNotFound(AuscultationPointNotFound ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(AuscultationPointInUseException.class)
    public ResponseEntity<Map<String, String>> handleInUse(AuscultationPointInUseException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", ex.getMessage()));
    }
}
