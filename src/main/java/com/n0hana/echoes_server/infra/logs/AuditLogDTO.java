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
        String action,
        String entity,
        String status,
        String details,
        String ip,
        Instant timestamp) {

    /**
     * Mapeia a entidade de domínio para o DTO de resposta.
     *
     * @param log Registro de auditoria persistido.
     * @return {@link AuditLogDTO} com os campos expostos na API.
     */
    public static AuditLogDTO from(AuditLog log) {
        return new AuditLogDTO(
                log.getId(),
                log.getUserId(),
                log.getAction(),
                log.getEntity(),
                log.getStatus(),
                log.getDetails(),
                log.getIp(),
                log.getTimestamp());
    }
}
