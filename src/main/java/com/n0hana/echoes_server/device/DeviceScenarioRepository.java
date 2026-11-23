package com.n0hana.echoes_server.device;

import org.javers.spring.annotation.JaversSpringDataAuditable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

@JaversSpringDataAuditable
public interface DeviceScenarioRepository extends JpaRepository<DeviceScenarioModel, UUID> {

  Optional<DeviceScenarioModel> findByDeviceIdAndPointId(UUID deviceId, UUID pointId);

  List<DeviceScenarioModel> findByDeviceId(UUID deviceId);

  boolean existsByPointId(UUID pointId);

  boolean existsByPointAnimalId(UUID animalId);

  boolean existsByScenarioId(UUID scenarioId);
}
