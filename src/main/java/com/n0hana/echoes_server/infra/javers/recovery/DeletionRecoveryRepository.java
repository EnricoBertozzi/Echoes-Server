package com.n0hana.echoes_server.infra.javers.recovery;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.n0hana.echoes_server.infra.javers.recovery.DeletionRecovery.Type;

interface DeletionRecoveryRepository extends JpaRepository<DeletionRecovery, UUID> {
    Optional<DeletionRecovery> findByTypeAndTargetId(Type type, UUID targetId);
}
