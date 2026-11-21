package com.n0hana.echoes_server.infra.javers.rollback;

/** O aprovador é sempre o usuário autenticado; o campo é opcional e, se enviado, precisa coincidir. */
public record JaversApproveDTO(String approvedBy) {
}
