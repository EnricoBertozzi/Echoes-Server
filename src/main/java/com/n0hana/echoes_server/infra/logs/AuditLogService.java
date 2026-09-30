package com.n0hana.echoes_server.infra.logs;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository repository;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onCommit(AuditLog logEvent) {
        if ("SUCCESS".equals(logEvent.getStatus())) {
            saveLog(logEvent);
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK, fallbackExecution = true)
    public void onRollback(AuditLog logEvent) {
        if ("SUCCESS".equals(logEvent.getStatus())) {
            logEvent.setStatus("FAILED");
            logEvent.setDetails("Transação sofreu rollback no banco de dados após a execução do método.");
        }
        saveLog(logEvent);
    }

    private void saveLog(AuditLog logEvent) {
        try {
            repository.save(logEvent);
        } catch (Exception e) {
            log.error("Falha crítica ao gravar log de auditoria. Ação: {}, Entidade: {}",
                    logEvent.getAction(), logEvent.getEntity(), e);
        }
    }

    public Page<AuditLog> getLogs(Pageable pageable) {
        return repository.findAll(pageable);
    }
}
