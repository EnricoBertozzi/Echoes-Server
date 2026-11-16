package com.n0hana.echoes_server.institution;

import java.time.Instant;

import org.javers.core.diff.changetype.ValueChange;

public record InstitutionChangeDTO(
        String author,
        Instant date,
        String property,
        Object before,
        Object after) {

    public static InstitutionChangeDTO from(ValueChange change) {
        var metadata = change.getCommitMetadata().orElseThrow();

        return new InstitutionChangeDTO(
                metadata.getAuthor(),
                metadata.getCommitDateInstant(),
                change.getPropertyName(),
                change.getLeft(),
                change.getRight());
    }
}
