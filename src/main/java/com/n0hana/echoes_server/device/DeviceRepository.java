package com.n0hana.echoes_server.device;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

  // ---- Dashboard ----

  /** Linhas {status, quantidade} de todos os dispositivos. */
  @Query("select d.status, count(d) from DeviceModel d group by d.status")
  List<Object[]> countByStatus();

  /** Linhas {status, quantidade} dos dispositivos de uma instituição. */
  @Query("select d.status, count(d) from DeviceModel d where d.institution.id = :institutionId group by d.status")
  List<Object[]> countByStatusInInstitution(@Param("institutionId") UUID institutionId);

  /** Linhas {instituição, status, quantidade} dos que têm instituição. */
  @Query("select d.institution.id, d.status, count(d) from DeviceModel d "
      + "where d.institution is not null group by d.institution.id, d.status")
  List<Object[]> countByInstitutionAndStatus();

  /** Registra o último contato sem carregar nem sobrescrever o dispositivo. */
  @Modifying
  @Query("update DeviceModel d set d.lastSeenAt = :at where d.id = :id")
  int touchLastSeen(@Param("id") UUID id, @Param("at") LocalDateTime at);
}
