package com.n0hana.echoes_server.term.exception;

public class TermNotFoundException extends RuntimeException {
    public TermNotFoundException() {
        super("Termo não encontrado");
    }

    public TermNotFoundException(String message) {
        super(message);
    }
}
