package com.n0hana.echoes_server.auscultation.exception;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AuscultationExceptionHandler {

    @ExceptionHandler(AuscultationPotionNotFound.class)
    public ResponseEntity<Map<String, String>> handleNotFound(AuscultationPotionNotFound ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", ex.getMessage()));
    }
}
