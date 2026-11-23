package com.n0hana.echoes_server.infra.javers.recovery;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "deletion_recovery", uniqueConstraints = @UniqueConstraint(columnNames = {"target_type", "target_id"}))
@Getter
@Setter
public class DeletionRecovery {

    public enum Type { CLASSROOM, INSTITUTION }
    public enum Status { AVAILABLE, PENDING_APPROVAL, APPROVED, EXECUTED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false)
    private Type type;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.AVAILABLE;

    private Instant deletedAt;
    private UUID requestedBy;
    private UUID approvedBy;
    private Instant restoredAt;
    private String commitId;

    // Nulo em exclusões antigas. Vazio quando a turma foi excluída sem integrantes.
    @Column(columnDefinition = "TEXT")
    private String memberIds;

    public List<UUID> memberIds() {
        if (memberIds == null || memberIds.isEmpty())
            return List.of();
        return Arrays.stream(memberIds.split(",")).map(UUID::fromString).toList();
    }
}
