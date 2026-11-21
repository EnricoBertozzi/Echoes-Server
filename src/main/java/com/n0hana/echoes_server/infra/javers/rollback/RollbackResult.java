package com.n0hana.echoes_server.infra.javers.rollback;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * @param affectedDevices        só preenchido para User: dispositivos da instituição do usuário, cujos tokens
 *                               não são revogados automaticamente
 * @param manualActionRequired   o que o administrador precisa fazer depois (só para User)
 */
public record RollbackResult(
        UUID entityId,
        String entityType,
        Instant restoredAt,
        List<UUID> affectedDevices,
        String manualActionRequired) {
}
