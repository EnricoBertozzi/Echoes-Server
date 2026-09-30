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

    /**
     * Executa numa thread separada (@Async) para não bloquear a requisição
     * principal.
     * Só é acionado se a transação do método principal for efetivada na base de
     * dados (AFTER_COMMIT).
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onCommit(AuditLog logEvent) {
        // Regista o log apenas se a operação principal tiver decorrido sem erros no
        // código
        if ("SUCCESS".equals(logEvent.getStatus())) {
            saveLog(logEvent);
        }
    }

    /**
     * Executa se a transação principal falhar e a base de dados realizar um
     * rollback (AFTER_ROLLBACK).
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK, fallbackExecution = true)
    public void onRollback(AuditLog logEvent) {
        // Corrige o status caso o método Java não tenha lançado exceção,
        // mas a transação tenha sido rejeitada pela base de dados no momento do commit.
        if ("SUCCESS".equals(logEvent.getStatus())) {
            logEvent.setStatus("FAILED");
            logEvent.setDetails("Transação sofreu rollback no banco de dados após a execução do método.");
        }
        saveLog(logEvent);
    }

    private void saveLog(AuditLog logEvent) {
        try {
            // Cria uma nova instância limpa ("fresh") em vez de persistir o objeto
            // recebido.
            // Como a transação original já foi encerrada (commit ou rollback),
            // utilizar a entidade original causaria problemas de contexto JPA (detached
            // entity).
            AuditLog fresh = AuditLog.builder()
                    .userId(logEvent.getUserId())
                    .action(logEvent.getAction())
                    .entity(logEvent.getEntity())
                    .status(logEvent.getStatus())
                    .details(logEvent.getDetails())
                    .ip(logEvent.getIp())
                    .build();

            repository.save(fresh);
        } catch (Exception e) {
            log.error("FALHA CRÍTICA ao gravar log de auditoria. Ação: {}, Entidade: {}",
                    logEvent.getAction(), logEvent.getEntity(), e);
        }
    }

    public Page<AuditLog> getLogs(Pageable pageable) {
        return repository.findAll(pageable);
    }
}
