package com.n0hana.echoes_server.cnpj.exception;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class CnpjExceptionHandler {

    @ExceptionHandler(CnpjInvalidoException.class)
    public ResponseEntity<Map<String, String>> handleInvalido(CnpjInvalidoException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(CnpjNaoEncontradoException.class)
    public ResponseEntity<Map<String, String>> handleNaoEncontrado(CnpjNaoEncontradoException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(CnpjProviderIndisponivelException.class)
    public ResponseEntity<Map<String, String>> handleIndisponivel(CnpjProviderIndisponivelException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("message", ex.getMessage()));
    }
}
