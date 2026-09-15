package com.n0hana.echoes_server.user.exception;

public class RegistrationAlreadyCompletedException extends RuntimeException {
    public RegistrationAlreadyCompletedException() {
        super("Cadastro já finalizado");
    }
}
