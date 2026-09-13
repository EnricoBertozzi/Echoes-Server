package com.n0hana.echoes_server.exception;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.HashMap;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.n0hana.echoes_server.user.exception.EmailAlreadyInUseException;
import com.n0hana.echoes_server.user.exception.ExpiredTwoFactorCodeException;
import com.n0hana.echoes_server.user.exception.InvalidTwoFactorCodeException;
import com.n0hana.echoes_server.user.exception.InvalidUserTypeException;
import com.n0hana.echoes_server.user.exception.RegistrationAlreadyCompletedException;
import com.n0hana.echoes_server.user.exception.UserNotFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(
            MethodArgumentNotValidException ex) {

        Map<String, String> errors = new HashMap<>();

        ex.getBindingResult().getFieldErrors().forEach(error -> {
            errors.put(error.getField(), error.getDefaultMessage());
        });

        return ResponseEntity.badRequest().body(errors);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleUserNotFound(
            UserNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(message(ex));
    }

    @ExceptionHandler(EmailAlreadyInUseException.class)
    public ResponseEntity<Map<String, String>> handleEmailAlreadyInUse(
            EmailAlreadyInUseException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(message(ex));
    }

    /**
     * Corrida de concorrência no e-mail único: a constraint do banco rejeita
     * o insert depois que a checagem de disponibilidade já tinha passado.
     * Só duplicidade (MySQL 1062 / SQLState 23505) vira 409 — outras
     * violações são re-lançadas para o 500 expor a causa real no log.
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

    @ExceptionHandler(RegistrationAlreadyCompletedException.class)
    public ResponseEntity<Map<String, String>> handleRegistrationAlreadyCompleted(
            RegistrationAlreadyCompletedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(message(ex));
    }

    @ExceptionHandler(InvalidTwoFactorCodeException.class)
    public ResponseEntity<Map<String, String>> handleInvalidTwoFactorCode(
            InvalidTwoFactorCodeException ex) {
        return ResponseEntity.badRequest().body(message(ex));
    }

    @ExceptionHandler(ExpiredTwoFactorCodeException.class)
    public ResponseEntity<Map<String, String>> handleExpiredTwoFactorCode(
            ExpiredTwoFactorCodeException ex) {
        return ResponseEntity.badRequest().body(message(ex));
    }

    @ExceptionHandler(InvalidUserTypeException.class)
    public ResponseEntity<Map<String, String>> handleInvalidUserType(
            InvalidUserTypeException ex) {
        return ResponseEntity.badRequest().body(message(ex));
    }

    private Map<String, String> message(RuntimeException ex) {
        return Map.of("message", ex.getMessage());
    }
}
