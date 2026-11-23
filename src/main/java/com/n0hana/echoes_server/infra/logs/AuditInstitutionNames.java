package com.n0hana.echoes_server.infra.logs;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class AuditInstitutionNames {

    private final NamedParameterJdbcTemplate jdbc;

    private record Institution(String name, boolean hasRecovery) {}

    Function<AuditLog, String> resolve(List<AuditLog> logs) {
        List<byte[]> ids = logs.stream()
                .filter(log -> recordedName(log) == null)
                .map(AuditInstitutionNames::institutionId)
                .filter(Objects::nonNull)
                .distinct()
                .map(id -> ByteBuffer.allocate(16)
                        .putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array())
                .toList();
        Map<UUID, Institution> institutions = new HashMap<>();
        if (!ids.isEmpty()) {
            // A consulta inclui instituições excluídas logicamente.
            jdbc.query("""
                    SELECT i.id, i.name, EXISTS (
                        SELECT 1 FROM deletion_recovery r
                        WHERE r.target_id = i.id AND r.target_type = 'INSTITUTION'
                    ) AS has_recovery
                    FROM institutions i WHERE i.id IN (:ids)
                    """, Map.of("ids", ids), row -> {
                ByteBuffer id = ByteBuffer.wrap(row.getBytes("id"));
                institutions.put(new UUID(id.getLong(), id.getLong()),
                        new Institution(row.getString("name"), row.getBoolean("has_recovery")));
            });
        }
        return log -> {
            String name = recordedName(log);
            if (name != null) return name;
            Institution institution = institutions.get(institutionId(log));
            return institution == null || (isHistoricalRecovery(log) && !institution.hasRecovery())
                    ? null : institution.name();
        };
    }

    private static String recordedName(AuditLog log) {
        boolean affectsInstitution = "Institution".equals(log.getEntity())
                || ("DeletionRecovery".equals(log.getEntity()) && log.getInstitutionId() != null);
        String name = log.getEntityName();
        return affectsInstitution && name != null && !name.isBlank() ? name : null;
    }

    private static boolean isHistoricalRecovery(AuditLog log) {
        return "DeletionRecovery".equals(log.getEntity()) && log.getInstitutionId() == null;
    }

    private static UUID institutionId(AuditLog log) {
        String id = log.getInstitutionId();
        if (id == null && ("Institution".equals(log.getEntity()) || isHistoricalRecovery(log))) {
            id = log.getEntityId();
        }
        if (id == null) return null;
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
