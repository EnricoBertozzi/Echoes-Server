package com.n0hana.echoes_server.infra.javers.rollback;

import java.util.UUID;

import com.n0hana.echoes_server.infra.javers.checkpoint.JaversCheckpoint;

public record JaversCheckpointDTO(UUID id, String commitId, String entityType, int affectedCount, String status) {

    public static JaversCheckpointDTO from(JaversCheckpoint checkpoint) {
        return new JaversCheckpointDTO(checkpoint.getId(), checkpoint.getCommitId(), checkpoint.getEntityType(),
                checkpoint.getAffectedCount(), checkpoint.getStatus().name());
    }
}
