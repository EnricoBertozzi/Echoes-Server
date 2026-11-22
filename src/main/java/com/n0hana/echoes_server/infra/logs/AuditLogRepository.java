
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

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("delete from AuditLog a where a.timestamp < :cutoff")
    int deleteOlderThan(@org.springframework.data.repository.query.Param("cutoff") Instant cutoff);

    List<AuditLog> findByUserIdOrderByTimestampDesc(String userId);
}

