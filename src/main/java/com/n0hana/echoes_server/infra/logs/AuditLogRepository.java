
package com.n0hana.echoes_server.infra.logs;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Repositório de registros de auditoria.
 *
 * JpaSpecificationExecutor permite combinar filtros
 * sem criar um método para cada combinação possível.
 */
public interface AuditLogRepository
        extends JpaRepository<AuditLog, UUID>,
                JpaSpecificationExecutor<AuditLog> {

    /**
     * Expurgo por prazo (L8). Só a rotina de retenção chama; no MySQL o gatilho
     * {@code audit_log_no_delete} recusa qualquer linha mais nova que o piso legal.
     */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("delete from AuditLog a where a.timestamp < :cutoff")
    int deleteOlderThan(@org.springframework.data.repository.query.Param("cutoff") Instant cutoff);

    /** registros de auditoria em que o próprio usuário é o autor (exportação LGPD) */
    List<AuditLog> findByUserIdOrderByTimestampDesc(String userId);
}

