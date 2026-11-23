package com.n0hana.echoes_server.infra.javers.rollback;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record JaversRollbackDTO(
        @NotNull UUID checkpointId,
        @NotBlank String entityType,
        @NotNull UUID entityId) {
}
