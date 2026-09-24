package com.n0hana.echoes_server.institution.exception;

import java.util.UUID;

/**
 * Lançada quando uma operação exige instituição verificada e a mesma
 * está em {@code PENDING_VERIFICATION} ou {@code REJECTED}.
 */
public class InstitutionPendingVerificationException extends RuntimeException {
    public InstitutionPendingVerificationException(UUID id) {
        super("Instituição " + id + " está com verificação de CNPJ pendente e não pode realizar esta operação");
    }
}
