package com.n0hana.echoes_server.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.n0hana.echoes_server.model.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    
}
