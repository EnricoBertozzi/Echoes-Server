package com.n0hana.echoes_server.user.dto;

import java.util.UUID;

import com.n0hana.echoes_server.user.UserRole;

/**
 * Resposta do passo 1 do registro: eco do convite aceito, aguardando a
 * confirmação via POST /users/2fa. Sem id (nada existe no banco ainda) e
 * sem código/expiração (segredo do canal de notificação).
 */
public record PendingRegistrationDTO(
    String name,
    String email,
    UserRole role,
    UUID institutionId
) {}
