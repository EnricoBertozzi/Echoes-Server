package com.n0hana.echoes_server.infra.javers.checkpoint;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Ponto de restauração criado antes de uma operação em lote; o rollback exige aprovação de outra pessoa. */
@Entity
@Table(name = "javers_checkpoint")
@Getter
@Setter
@NoArgsConstructor
public class JaversCheckpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** último commit do Javers feito para o checkpoint; o rollback restaura o estado até ele */
    @Column(name = "commit_id", nullable = false)
    private String commitId;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "affected_count", nullable = false)
    private int affectedCount;

    /** ids das entidades do checkpoint, separados por vírgula */
    @Column(name = "entity_ids", columnDefinition = "TEXT")
    private String entityIds;

    @Column(name = "requested_by", nullable = false)
    private String requestedBy;

    @Column(name = "approved_by")
    private String approvedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private JaversCheckpointStatus status = JaversCheckpointStatus.PENDING_APPROVAL;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "executed_at")
    private Instant executedAt;
}
