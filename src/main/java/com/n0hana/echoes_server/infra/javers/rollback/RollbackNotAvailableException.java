package com.n0hana.echoes_server.infra.javers.rollback;

public class RollbackNotAvailableException extends RuntimeException {

    public RollbackNotAvailableException(String message) {
        super(message);
    }
}
