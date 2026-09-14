package com.n0hana.echoes_server.user;

import java.time.Instant;
import java.util.UUID;

/**
 * Dados de um convite de cadastro aguardando confirmação (passo 2 do fluxo
 * de registro). Persiste apenas no Redis: nenhum usuário existe no banco
 * antes de o código 2FA ser verificado com a senha.
 */
public record PendingRegistration(
    String name,
    String email,
    String role,
    UUID institutionId,
    String code,
    Instant expiresAt,
    int attempts,
    Instant createdAt
) {}
