package com.n0hana.echoes_server.auth.exception;

/**
 * Exceção não tratada 
 */
public class UserBlockedException extends RuntimeException {
    
    public UserBlockedException() {
        super("Usuário bloqueado");
    }
}
