package com.n0hana.echoes_server.auth.exception;

public class PasswordUnchangedException extends RuntimeException {

    public PasswordUnchangedException() {
        super("A nova senha deve ser diferente da senha atual");
    }
}
