package com.n0hana.echoes_server.infra.file.exception;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Tratamento de exceções do subsistema de arquivos.
 */
@RestControllerAdvice
public class FileStorageExceptionHandler {

    @ExceptionHandler(FileValidateException.class)
    public ResponseEntity<Map<String, String>> handleValidate(FileValidateException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(StorageFileNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(StorageFileNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler({ StorageSaveFileException.class, StorageDeleteFileException.class })
    public ResponseEntity<Map<String, String>> handleStorage(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", ex.getMessage()));
    }
}
