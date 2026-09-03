package com.n0hana.echoes_server.dto;

import java.time.Instant;
import java.util.UUID;

import com.n0hana.echoes_server.model.AuditLog;

public record AuditLogResponseDTO(
    UUID id,
    String userId,
    String action,
    String entity,
    String status,
    String details,
    String ip,
    Instant timestamp
) {
    public AuditLogResponseDTO(AuditLog log) {
        this(
            log.getId(),
            log.getUserId(),
            log.getAction(),
            log.getEntity(),
            log.getStatus(),
            log.getDetails(),
            log.getIp(),
            log.getTimestamp()
        );
    }
}
