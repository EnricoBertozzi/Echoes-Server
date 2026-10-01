package com.n0hana.echoes_server.institution.exception;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class InstitutionExceptionHandler {

    @ExceptionHandler(InstitutionNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(InstitutionNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(InstitutionPendingVerificationException.class)
    public ResponseEntity<Map<String, String>> handlePending(InstitutionPendingVerificationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", ex.getMessage()));
    }
}
