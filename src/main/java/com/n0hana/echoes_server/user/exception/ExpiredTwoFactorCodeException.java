package com.n0hana.echoes_server.user.exception;

public class ExpiredTwoFactorCodeException extends RuntimeException {
    public ExpiredTwoFactorCodeException() {
        super("Código expirado");
    }
}
