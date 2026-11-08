package com.n0hana.echoes_server.animal.exception;

public class InvalidPublicKeyException extends RuntimeException {

    public InvalidPublicKeyException() {
        super("Chave pública inválida");
    }
}
