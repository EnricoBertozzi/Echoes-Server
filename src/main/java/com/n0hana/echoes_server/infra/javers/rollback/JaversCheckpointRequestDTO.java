package com.n0hana.echoes_server.infra.javers.rollback;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record JaversCheckpointRequestDTO(
        @NotBlank String entityType,
        @NotEmpty @Size(max = 1000) List<UUID> entityIds) {
}
