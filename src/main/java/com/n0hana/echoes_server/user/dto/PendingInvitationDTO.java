package com.n0hana.echoes_server.user.dto;

import java.time.Instant;
import java.util.UUID;

import com.n0hana.echoes_server.user.model.UserRole;

/**
 * Convite de cadastro ainda válido. Não traz código nem tentativas (segredo do
 * canal de notificação), só o que a tela precisa para mostrar quem foi convidado
 * e até quando o convite vale.
 */
public record PendingInvitationDTO(
        String name,
        String email,
        UserRole role,
        UUID institutionId,
        Instant invitedAt,
        Instant expiresAt) {
}
