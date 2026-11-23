
package com.n0hana.echoes_server.infra.logs;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.access.AccessDeniedException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import org.springframework.web.server.ResponseStatusException;

import com.n0hana.echoes_server.device.port.DeviceScopeResolver;
import com.n0hana.echoes_server.user.model.User;
import com.n0hana.echoes_server.user.model.UserRole;

import jakarta.persistence.criteria.Predicate;

import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Propagation;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository repository;
    private final DeviceScopeResolver scopeResolver;
    private final AuditSigner auditSigner;

    /**
     * Persiste eventos confirmados pela transação.
     */
    @Async
    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true
    )
    public void onCommit(AuditLog logEvent) {
        if ("SUCCESS".equals(logEvent.getStatus())) {
            saveLog(logEvent);
        }
    }

    /**
     * Registra operações que sofreram rollback.
     */
    @Async
    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_ROLLBACK,
            fallbackExecution = false
    )
    public void onRollback(AuditLog logEvent) {
        if ("SUCCESS".equals(logEvent.getStatus())) {
            logEvent.setStatus("FAILED");
            logEvent.setDetails(
                "Transação sofreu rollback no banco de dados após a execução do método."
            );
        }

        saveLog(logEvent);
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(AuditLog logEvent) {
        saveLog(logEvent);
    }

    /**
     * Grava o resultado de um envio de e-mail, em transação própria: não depende
     * do método que pediu o envio (que pode já ter terminado ou sido revertido).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordEmailOutcome(AuditLog logEvent) {
        saveLog(logEvent);
    }

    /**
     * Persiste uma instância nova para evitar problemas
     * com entidades detached.
     */
    private void saveLog(AuditLog logEvent) {
        try {
            AuditLog fresh = AuditLog.builder()
                    .userId(logEvent.getUserId())
                    .action(logEvent.getAction())
                    .entity(logEvent.getEntity())
                    .status(logEvent.getStatus())
                    .details(logEvent.getDetails())
                    .ip(logEvent.getIp())
                    .entityId(logEvent.getEntityId())
                    .entityName(logEvent.getEntityName())
                    .actorName(logEvent.getActorName())
                    .actorRole(logEvent.getActorRole())
                    .actorInstitutionName(logEvent.getActorInstitutionName())
                    .institutionId(logEvent.getInstitutionId())
                    .build();

            fresh.setTimestamp(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
            fresh.setSignature(auditSigner.sign(fresh));

            repository.save(fresh);

        } catch (Exception e) {
            log.error(
                "FALHA CRÍTICA ao gravar log de auditoria. Ação: {}, Entidade: {}",
                logEvent.getAction(),
                logEvent.getEntity(),
                e
            );
        }
    }

    /**
     * Consulta paginada com aplicação obrigatória de RBAC.
     *
     * ADMIN:
     * - Acessa todos os registros.
     * - Sem institutionId inclui eventos globais.
     * - Pode filtrar qualquer instituição.
     *
     * MANAGER:
     * - Acessa somente sua própria instituição.
     * - institutionId ausente mantém o escopo próprio.
     * - institutionId diferente resulta em 403.
     *
     * TEACHER/STUDENT:
     * - Acesso negado.
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> getLogs(
            User user,
            UUID requestedInstitutionId,
            String entity,
            String action,
            String status,
            Pageable pageable
    ) {
        if (
            user == null ||
            (
                user.getUserRole() != UserRole.ADMIN &&
                user.getUserRole() != UserRole.MANAGER
            )
        ) {
            throw new AccessDeniedException(
                "Sem acesso aos registros de auditoria"
            );
        }

        /*
         * Reutiliza a política de escopo já implementada
         * para dispositivos e Dashboard.
         *
         * O servidor calcula a instituição efetiva
         * a partir do usuário autenticado.
         */
        UUID institutionId = scopeResolver
                .resolve(user)
                .effectiveInstitution(requestedInstitutionId);

        String selectedEntity = nonBlank(entity);
        String selectedAction = nonBlank(action);
        String selectedStatus = nonBlank(status);

        if (
            selectedStatus != null &&
            !"SUCCESS".equals(selectedStatus) &&
            !"FAILED".equals(selectedStatus)
        ) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Status de auditoria inválido"
            );
        }

        Specification<AuditLog> specification =
                (root, query, builder) -> {

                    List<Predicate> predicates =
                            new ArrayList<>();

                    /*
                     * O institutionId em AuditLog é String.
                     *
                     * Para MANAGER, a condição institucional
                     * nunca pode ser omitida.
                     *
                     * Para ADMIN, null significa consulta
                     * global, sem restrição institucional.
                     */
                    if (institutionId != null) {
                        predicates.add(
                            builder.equal(
                                root.get("institutionId"),
                                institutionId.toString()
                            )
                        );
                    }

                    if (user.getUserRole() == UserRole.MANAGER) {
                        // O fluxo de restaurações é reservado aos administradores.
                        predicates.add(builder.or(root.get("entity").isNull(),
                                builder.notEqual(root.get("entity"), "DeletionRecovery")));
                    }

                    if (selectedEntity != null) {
                        predicates.add(
                            builder.equal(
                                root.get("entity"),
                                selectedEntity
                            )
                        );
                    }

                    if (selectedAction != null) {
                        predicates.add(
                            builder.equal(
                                root.get("action"),
                                selectedAction
                            )
                        );
                    }

                    if (selectedStatus != null) {
                        predicates.add(
                            builder.equal(
                                root.get("status"),
                                selectedStatus
                            )
                        );
                    }

                    return builder.and(
                        predicates.toArray(new Predicate[0])
                    );
                };

        return repository.findAll(
            specification,
            pageable
        );
    }

    private static String nonBlank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    @Transactional(readOnly = true)
    public java.util.List<UUID> findTamperedIds() {
        return repository.findAll().stream()
                .filter(log -> !auditSigner.verify(log))
                .map(AuditLog::getId)
                .toList();
    }
}
