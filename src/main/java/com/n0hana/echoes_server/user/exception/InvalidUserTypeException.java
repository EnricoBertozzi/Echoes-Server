package com.n0hana.echoes_server.user.exception;

public class InvalidUserTypeException extends RuntimeException {
    public InvalidUserTypeException() {
        super("Tipo de usuário inválido para esta operação");
    }
}
