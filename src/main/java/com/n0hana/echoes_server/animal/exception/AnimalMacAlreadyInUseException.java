package com.n0hana.echoes_server.animal.exception;

public class AnimalMacAlreadyInUseException extends RuntimeException {

    public AnimalMacAlreadyInUseException() {
        super("MAC já cadastrado para outro animal");
    }
}
