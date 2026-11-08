package com.n0hana.echoes_server.device;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceRepository extends JpaRepository<DeviceModel, UUID> {

  @EntityGraph(attributePaths = "animal")
  Optional<DeviceModel> findByMac(String mac);

  @EntityGraph(attributePaths = "animal")
  Optional<DeviceModel> findById(UUID id);

  @Override
  @EntityGraph(attributePaths = "animal")
  Page<DeviceModel> findAll(Pageable pageable);

  @EntityGraph(attributePaths = "animal")
  Page<DeviceModel> findByNameContaining(String name, Pageable pageable);
}
