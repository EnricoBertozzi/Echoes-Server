package com.n0hana.echoes_server.user.exception;

public class LastAdminException extends RuntimeException {
    public LastAdminException() {
        super("O sistema não pode ficar sem administrador ativo");
    }
}
