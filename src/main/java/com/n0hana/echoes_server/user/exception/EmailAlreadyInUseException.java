package com.n0hana.echoes_server.user.exception;

public class EmailAlreadyInUseException extends RuntimeException {
    public EmailAlreadyInUseException() {
        super("E-mail já cadastrado");
    }
}
