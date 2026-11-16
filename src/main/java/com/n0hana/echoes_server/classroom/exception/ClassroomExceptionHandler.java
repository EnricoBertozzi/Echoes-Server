package com.n0hana.echoes_server.classroom.exception;

import java.util.Map;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// tratamento de exceções do domínio de turmas
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ClassroomExceptionHandler {

    @ExceptionHandler(ClassroomNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(ClassroomNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(ClassroomForbiddenException.class)
    public ResponseEntity<Map<String, String>> handleForbidden(ClassroomForbiddenException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(ClassroomArchivedException.class)
    public ResponseEntity<Map<String, String>> handleArchived(ClassroomArchivedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(ClassroomCodeActiveException.class)
    public ResponseEntity<Map<String, String>> handleCodeActive(ClassroomCodeActiveException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(ClassroomStudentEnrolledException.class)
    public ResponseEntity<Map<String, String>> handleStudentEnrolled(ClassroomStudentEnrolledException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", ex.getMessage()));
    }
}
