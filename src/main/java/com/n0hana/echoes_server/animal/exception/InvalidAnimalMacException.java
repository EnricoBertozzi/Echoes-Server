package com.n0hana.echoes_server.animal.exception;

public class InvalidAnimalMacException extends RuntimeException {

    public InvalidAnimalMacException() {
        super("MAC do animal é obrigatório");
    }
}
