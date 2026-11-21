package com.n0hana.echoes_server.infra.javers.rollback;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * @param candidateDevices       só preenchido para User: dispositivos da instituição do usuário restaurado. É uma
 *                               aproximação (o modelo não liga usuário a dispositivo); nenhum token é revogado
 *                               automaticamente
 * @param manualActionRequired   o que o administrador precisa fazer depois (só para User)
 */
public record RollbackResult(
        UUID entityId,
        String entityType,
        Instant restoredAt,
        List<UUID> candidateDevices,
        String manualActionRequired) {
}
