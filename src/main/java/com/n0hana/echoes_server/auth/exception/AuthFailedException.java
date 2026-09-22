package com.n0hana.echoes_server.auth.exception;

public class AuthFailedException extends RuntimeException {
    public AuthFailedException() {
        super("Erro ao autenticar");
    }
}
