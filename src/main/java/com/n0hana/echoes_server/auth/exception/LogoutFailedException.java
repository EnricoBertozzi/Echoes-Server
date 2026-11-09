package com.n0hana.echoes_server.auth.exception;

/**
 * Exceção para falhas durante o processo de logout
 * 
 * @author Enrico Bertozzi
 */
public class LogoutFailedException extends RuntimeException {
    
    public LogoutFailedException() {
        super("Falha ao realizar o logout");
    }
}
