package com.n0hana.echoes_server.user.exception;

public class InvalidTwoFactorCodeException extends RuntimeException {
    public InvalidTwoFactorCodeException() {
        super("Código inválido");
    }
}
