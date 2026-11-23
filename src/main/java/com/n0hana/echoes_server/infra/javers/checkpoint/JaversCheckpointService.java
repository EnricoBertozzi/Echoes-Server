package com.n0hana.echoes_server.infra.javers.checkpoint;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;

import org.javers.core.commit.Commit;
import org.javers.core.Javers;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class JaversCheckpointService {

    private final JaversCheckpointRepository repository;
    private final Javers javers;
    private final EntityManagerFactory entityManagerFactory;

    @PersistenceContext
    private EntityManager em;

    /** Registra o estado atual das entidades no Javers e guarda o checkpoint aguardando aprovação. */
    @Transactional
    public JaversCheckpoint create(String entityType, List<Object> entities, String requestedBy) {
        if (entities == null || entities.isEmpty())
            throw new IllegalArgumentException("Checkpoint sem entidades");

        String commitId = null;
        for (Object entity : entities) {
            Commit commit = javers.commit(requestedBy + ":pre-bulk:" + entityType, entity);
            commitId = commit.getId().value();
        }

        JaversCheckpoint checkpoint = new JaversCheckpoint();
        checkpoint.setCommitId(commitId);
        checkpoint.setEntityType(entityType);
        checkpoint.setAffectedCount(entities.size());
        checkpoint.setEntityIds(entities.stream()
                .map(e -> String.valueOf(entityManagerFactory.getPersistenceUnitUtil().getIdentifier(e)))
                .collect(Collectors.joining(",")));
        checkpoint.setRequestedBy(requestedBy);
        checkpoint.setCreatedAt(Instant.now());
        return repository.save(checkpoint);
    }

    /** Cria o checkpoint a partir de ids: carrega as entidades do tipo informado e registra o estado atual. */
    @Transactional
    public JaversCheckpoint createForIds(Class<?> type, List<UUID> ids, String requestedBy) {
        if (ids == null || ids.isEmpty())
            throw new IllegalArgumentException("Checkpoint sem entidades");

        List<Object> entities = new java.util.ArrayList<>();
        for (UUID id : ids) {
            Object entity = em.find(type, id);
            if (entity == null)
                throw new NoSuchElementException("Entidade não encontrada: " + type.getSimpleName() + " " + id);
            entities.add(entity);
        }
        return create(type.getName(), entities, requestedBy);
    }

    /** Quem aprova precisa ser diferente de quem pediu. */
    @Transactional
    public void approve(UUID checkpointId, String approvedBy) {
        JaversCheckpoint checkpoint = find(checkpointId);

        if (checkpoint.getStatus() != JaversCheckpointStatus.PENDING_APPROVAL)
            throw new IllegalStateException("Checkpoint não está aguardando aprovação");
        if (approvedBy == null || approvedBy.isBlank() || approvedBy.equals(checkpoint.getRequestedBy()))
            throw new IllegalStateException("Quem aprova precisa ser diferente de quem solicitou");

        checkpoint.setApprovedBy(approvedBy);
        checkpoint.setStatus(JaversCheckpointStatus.APPROVED);
        repository.save(checkpoint);
    }

    public JaversCheckpoint requireApproved(UUID checkpointId) {
        JaversCheckpoint checkpoint = find(checkpointId);
        if (checkpoint.getStatus() != JaversCheckpointStatus.APPROVED)
            throw new IllegalStateException("Checkpoint não está aprovado");
        return checkpoint;
    }

    @Transactional
    public void markExecuted(UUID checkpointId) {
        JaversCheckpoint checkpoint = find(checkpointId);
        checkpoint.setStatus(JaversCheckpointStatus.EXECUTED);
        checkpoint.setExecutedAt(Instant.now());
        repository.save(checkpoint);
    }

    private JaversCheckpoint find(UUID checkpointId) {
        return repository.findById(checkpointId)
                .orElseThrow(() -> new NoSuchElementException("Checkpoint não encontrado"));
    }
}
