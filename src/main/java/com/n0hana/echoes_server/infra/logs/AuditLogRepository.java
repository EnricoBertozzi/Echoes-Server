
package com.n0hana.echoes_server.infra.logs;

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

    /** registros de auditoria em que o próprio usuário é o autor (exportação LGPD) */
    List<AuditLog> findByUserIdOrderByTimestampDesc(String userId);
}

