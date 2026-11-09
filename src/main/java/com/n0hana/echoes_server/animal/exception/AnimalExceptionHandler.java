package com.n0hana.echoes_server.animal.exception;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Tratamento de exceções do domínio de animais.
 */
@RestControllerAdvice
public class AnimalExceptionHandler {

    @ExceptionHandler(AnimalNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(AnimalNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", ex.getMessage()));
    }
}
