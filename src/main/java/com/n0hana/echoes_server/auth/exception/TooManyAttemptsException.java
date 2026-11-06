package com.n0hana.echoes_server.auth.exception;

public class TooManyAttemptsException extends RuntimeException {
    public TooManyAttemptsException() {
        super("Muitas tentativas. Tente novamente mais tarde");
    }
}
