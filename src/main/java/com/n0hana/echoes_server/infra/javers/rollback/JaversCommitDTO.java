package com.n0hana.echoes_server.infra.javers.rollback;

import java.time.Instant;

import org.javers.core.metamodel.object.CdoSnapshot;

public record JaversCommitDTO(String commitId, String author, Instant timestamp, String type) {

    public static JaversCommitDTO from(CdoSnapshot snapshot) {
        return new JaversCommitDTO(
                snapshot.getCommitId().value(),
                snapshot.getCommitMetadata().getAuthor(),
                snapshot.getCommitMetadata().getCommitDateInstant(),
                snapshot.getType().name());
    }
}
