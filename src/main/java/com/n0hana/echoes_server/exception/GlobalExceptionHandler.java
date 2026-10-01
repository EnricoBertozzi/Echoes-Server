package com.n0hana.echoes_server.exception;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.HashMap;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Tratamento de exceções transversais (Bean Validation, integridade de dados).
 *
 * <p>
 * Exceções de domínio são tratadas nos {@code *ExceptionHandler} de cada
 * pacote ({@code auth}, {@code user}, {@code institution}, {@code cnpj},
 * {@code animal}, {@code auscultation}, {@code scenario}, {@code term},
 * {@code infra.file}), evitando uma God Class.
 * </p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(
            MethodArgumentNotValidException ex) {

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest().body(errors);
    }

    /**
     * Corrida de concorrência no e-mail único: a constraint do banco rejeita
     * o insert depois que a checagem de disponibilidade já tinha passado.
     * Só duplicidade (MySQL 1062 / SQLState 23505) vira 409 — outras
     * violações são relançadas para o 500 expor a causa real no log.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrityViolation(
            DataIntegrityViolationException ex) {
        if (isEmailDuplicate(ex)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "E-mail já cadastrado"));
        }
        throw ex;
    }

    private boolean isEmailDuplicate(DataIntegrityViolationException ex) {
        if (ex.getMostSpecificCause() instanceof SQLIntegrityConstraintViolationException sqlEx) {
            return sqlEx.getErrorCode() == 1062 || "23505".equals(sqlEx.getSQLState());
        }
        return false;
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Map<String, String>> handleDuplicateKeyException(DuplicateKeyException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }
}
