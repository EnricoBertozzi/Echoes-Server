package com.n0hana.echoes_server.infra.logs;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO de leitura para registros de auditoria.
 *
 * <p>
 * Mantém o {@link AuditLog} encapsulado como entidade JPA: campos internos
 * que forem adicionados à entidade no futuro não vazam automaticamente para
 * o contrato HTTP. A serialização de saída fica explícita aqui.
 * </p>
 *
 * @author Enrico Bertozzi
 * @since 0.1.2
 */
public record AuditLogDTO(
        UUID id,
        String userId,
        String actorName,
        String actorRole,
        String actorInstitutionName,
        String action,
        String entity,
        String entityName,
        String status,
        String details,
        String ip,
        String entityId,
        String institutionId,
        Instant timestamp) {

    /**
     * Mapeia a entidade de domínio para o DTO de resposta.
     *
     * @param log Registro de auditoria persistido.
     * @return {@link AuditLogDTO} com os campos expostos na API.
     */
    public static AuditLogDTO from(AuditLog log) {
        return from(log, true);
    }

    /**
     * Mapeia a entidade de domínio para o DTO de resposta, controlando a
     * exposição do IP.
     *
     * @param log       Registro de auditoria persistido.
     * @param includeIp {@code true} para incluir o IP; {@code false} devolve
     *                  {@code null}.
     * @return {@link AuditLogDTO} com os campos expostos na API.
     */
    public static AuditLogDTO from(AuditLog log, boolean includeIp) {
        return new AuditLogDTO(
                log.getId(),
                log.getUserId(),
                log.getActorName(),
                log.getActorRole(),
                log.getActorInstitutionName(),
                log.getAction(),
                log.getEntity(),
                log.getEntityName(),
                log.getStatus(),
                log.getDetails(),
                includeIp ? log.getIp() : null,
                log.getEntityId(),
                log.getInstitutionId(),
                log.getTimestamp());
    }
}
