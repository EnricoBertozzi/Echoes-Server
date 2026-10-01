package com.n0hana.echoes_server.user.exception;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Tratamento de exceções do domínio de usuários e fluxo de registro.
 */
@RestControllerAdvice
public class UserExceptionHandler {

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(UserNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(message(ex));
    }

    @ExceptionHandler(EmailAlreadyInUseException.class)
    public ResponseEntity<Map<String, String>> handleEmailAlreadyInUse(EmailAlreadyInUseException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(message(ex));
    }

    @ExceptionHandler(RegistrationAlreadyCompletedException.class)
    public ResponseEntity<Map<String, String>> handleRegistrationAlreadyCompleted(
            RegistrationAlreadyCompletedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(message(ex));
    }

    @ExceptionHandler(RequiredTermsNotAcceptedException.class)
    public ResponseEntity<Map<String, String>> handleRequiredTermsNotAccepted(
            RequiredTermsNotAcceptedException ex) {
        return ResponseEntity.badRequest().body(message(ex));
    }

    @ExceptionHandler(InvalidTwoFactorCodeException.class)
    public ResponseEntity<Map<String, String>> handleInvalidTwoFactorCode(InvalidTwoFactorCodeException ex) {
        return ResponseEntity.badRequest().body(message(ex));
    }

    @ExceptionHandler(ExpiredTwoFactorCodeException.class)
    public ResponseEntity<Map<String, String>> handleExpiredTwoFactorCode(ExpiredTwoFactorCodeException ex) {
        return ResponseEntity.badRequest().body(message(ex));
    }

    @ExceptionHandler(InvalidUserTypeException.class)
    public ResponseEntity<Map<String, String>> handleInvalidUserType(InvalidUserTypeException ex) {
        return ResponseEntity.badRequest().body(message(ex));
    }

    private Map<String, String> message(RuntimeException ex) {
        return Map.of("message", ex.getMessage());
    }
}
