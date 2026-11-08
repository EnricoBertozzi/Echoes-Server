package com.n0hana.echoes_server.auth.exception;

/**
 * Exceção para o erro de código de autenticação multifator
 * 
 * @author Enrico Bertozzi
 */
public class InvalidMfaCodeException extends RuntimeException {

    public InvalidMfaCodeException() {
        super("Código Inválido");
    }
}
