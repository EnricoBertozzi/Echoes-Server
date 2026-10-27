package com.n0hana.echoes_server.exception;

import java.util.HashMap;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import lombok.extern.slf4j.Slf4j;

/**
 * Tratamento de exceções <b>transversais</b> (Bean Validation, HTTP, integridade
 * de dados crua). Exceções de domínio ficam nos {@code *ExceptionHandler} dos
 * respectivos pacotes ({@code auth}, {@code user}, {@code institution},
 * {@code cnpj}, {@code animal}, {@code auscultation}, {@code scenario},
 * {@code term}, {@code infra.file}, {@code infra.security}).
 *
 * <p><b>Segurança:</b> a resposta do handler genérico nunca expõe mensagem do
 * {@code Throwable} — apenas um texto fixo. O stack trace vai para o log.</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // validação

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(
            MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest().body(errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleMessageNotReadable(
            HttpMessageNotReadableException ex) {
        log.warn("Requisição com corpo mal formatado: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(Map.of("message", "Corpo da requisição mal formatado"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("message", "Parâmetro '" + ex.getName() + "' inválido"));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, String>> handleMissingParameter(
            MissingServletRequestParameterException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("message", "Parâmetro obrigatório ausente: " + ex.getParameterName()));
    }

    //http
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, String>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(Map.of("message", "Método HTTP não suportado"));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, String>> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(Map.of("message", "Tipo de mídia não suportado"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, String>> handleNoResourceFound(
            NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", "Recurso não encontrado"));
    }

    // exceções que já trazem status

    @ExceptionHandler(ErrorResponseException.class)
    public ResponseEntity<Map<String, String>> handleErrorResponse(ErrorResponseException ex) {
        String detail = ex.getBody().getDetail();
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("message", detail != null ? detail : "Erro na requisição"));
    }

    // persistência

    
     //Violação de integridade que chega até aqui não é duplicidade de domínioconhecida.
     //Casos esperados (ex.: corrida de e-mail no cadastro de usuário) são convertidos em exceções específicas no {@code UserService} — ver
     //{@code UserService.completeRegistration}.
     
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrityViolation(
            DataIntegrityViolationException ex) {
        log.error("Violação de integridade não tratada pelo domínio", ex);
        throw ex;
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Map<String, String>> handleDuplicateKeyException(
            DuplicateKeyException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }

    // fallback

     //Qualquer exceção não classificada retorna 500 sem expor detalhes.
     //O stack trace é registrado no log — o cliente recebe mensagem fixa.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneric(Exception ex) {
        log.error("Exceção não tratada", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "Erro interno do servidor"));
    }
}
