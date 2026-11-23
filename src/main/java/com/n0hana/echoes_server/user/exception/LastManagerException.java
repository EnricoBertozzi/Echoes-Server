package com.n0hana.echoes_server.user.exception;

public class LastManagerException extends RuntimeException {
    public LastManagerException() {
        super("A instituição não pode ficar sem gestor");
    }
}
