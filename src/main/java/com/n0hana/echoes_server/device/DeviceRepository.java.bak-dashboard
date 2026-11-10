package com.n0hana.echoes_server.device;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * A instituição entra no grafo porque o filtro de segurança decide se o
 * dispositivo pode operar fora de uma transação, sem carregamento tardio.
 */
public interface DeviceRepository extends JpaRepository<DeviceModel, UUID> {

  @EntityGraph(attributePaths = { "animal", "institution" })
  Optional<DeviceModel> findByHardwareId(String hardwareId);

  @EntityGraph(attributePaths = { "animal", "institution" })
  Optional<DeviceModel> findById(UUID id);

  @Override
  @EntityGraph(attributePaths = { "animal", "institution" })
  Page<DeviceModel> findAll(Pageable pageable);

  @EntityGraph(attributePaths = { "animal", "institution" })
  Page<DeviceModel> findByNameContaining(String name, Pageable pageable);

  @EntityGraph(attributePaths = { "animal", "institution" })
  Page<DeviceModel> findByStatus(DeviceStatus status, Pageable pageable);
}
