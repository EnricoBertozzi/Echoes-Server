package com.n0hana.echoes_server.service.logs;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.n0hana.echoes_server.model.AuditLog;
import com.n0hana.echoes_server.repository.AuditLogRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuditLogService {
    
    private final AuditLogRepository repository;

    @Async
    public void register(AuditLog log) {
        repository.save(log);
    }

    public Page<AuditLog> getLogs(Pageable pageable) {
        return repository.findAll(pageable);
    }
}
