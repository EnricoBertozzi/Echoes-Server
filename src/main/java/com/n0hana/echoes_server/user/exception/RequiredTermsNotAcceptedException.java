package com.n0hana.echoes_server.user.exception;

import java.util.List;

import com.n0hana.echoes_server.term.model.DocumentType;

public class RequiredTermsNotAcceptedException extends RuntimeException {

    private final List<DocumentType> missing;

    public RequiredTermsNotAcceptedException(List<DocumentType> missing) {
        super("Termos obrigatórios não aceitos: " + missing);
        this.missing = missing;
    }

    public List<DocumentType> getMissing() {
        return missing;
    }
}
