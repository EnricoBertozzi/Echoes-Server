package com.n0hana.echoes_server.institution;

public enum InstitutionVerificationStatus {
    /** Verificada com sucesso na Receita Federal. */
    VERIFIED,
    /** Aguardando verificação; Brasil API indisponível no cadastro. */
    PENDING_VERIFICATION,
    /** CNPJ informado não existe na Receita Federal. Bloqueada até ação do admin. */
    REJECTED
}
