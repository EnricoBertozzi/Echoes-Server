package com.n0hana.echoes_server.institution.exception;

 //Lança quando já existe uma instituição com o mesmo CNPJ ou e-mail.

 //Substitui o uso genérico de {@code DuplicateKeyException} no
 //link InstitutionService para que
 //o tratamento fique no contexto do domínio de instituições e a resposta
 //HTTP seja semanticamente correta (409 Conflict)
public class InstitutionAlreadyExistsException extends RuntimeException {
    public InstitutionAlreadyExistsException(String message) {
        super(message);
    }
}
