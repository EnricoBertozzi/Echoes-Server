package com.n0hana.echoes_server.auth.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

//Tratamento de exceções do domínio de autenticação.
@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler(AuthFailedException.class)
    public ResponseEntity<Map<String, String>> handleAuthFailed(AuthFailedException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(TooManyAttemptsException.class)
    public ResponseEntity<Map<String, String>> handleTooManyAttempts(TooManyAttemptsException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("message", ex.getMessage()));
    }
}
