package com.n0hana.echoes_server.infra.javers.checkpoint;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JaversCheckpointRepository extends JpaRepository<JaversCheckpoint, UUID> {
}
