package com.n0hana.echoes_server.institution.exception;

/**
 * Exceção não verificada para representar falha na busca por instituições.
*/
public class InstitutionNotFoundException extends RuntimeException {
    public InstitutionNotFoundException(String message) {
        super(message);
    }
}
