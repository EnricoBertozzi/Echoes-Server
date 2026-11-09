package com.n0hana.echoes_server.device.event;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Consultas agregadas da dashboard. Todas aceitam "todas as instituições"
 * ({@code allInstitutions = true}, e então {@code institutionId} é ignorado) ou
 * uma instituição só. Quem chama passa um UUID qualquer quando é "todas" (nunca
 * nulo), para não depender de como cada banco tipa parâmetro nulo.
 */
public interface DeviceEventRepository extends JpaRepository<DeviceEventModel, UUID> {

  /** Linhas {dia, tipo, quantidade}. */
  @Query("""
      select e.occurredOn, e.type, count(e)
      from DeviceEventModel e
      where e.occurredOn between :from and :to
        and (:allInstitutions = true or e.institutionId = :institutionId)
      group by e.occurredOn, e.type
      """)
  List<Object[]> countByDayAndType(@Param("from") LocalDate from, @Param("to") LocalDate to,
      @Param("allInstitutions") boolean allInstitutions, @Param("institutionId") UUID institutionId);

  @Query("""
      select count(e)
      from DeviceEventModel e
      where e.type = :type
        and e.occurredOn between :from and :to
        and (:allInstitutions = true or e.institutionId = :institutionId)
      """)
  long countByType(@Param("type") DeviceEventType type, @Param("from") LocalDate from,
      @Param("to") LocalDate to, @Param("allInstitutions") boolean allInstitutions,
      @Param("institutionId") UUID institutionId);

  @Query("""
      select count(distinct e.deviceId)
      from DeviceEventModel e
      where e.occurredOn between :from and :to
        and (:allInstitutions = true or e.institutionId = :institutionId)
      """)
  long countDistinctDevices(@Param("from") LocalDate from, @Param("to") LocalDate to,
      @Param("allInstitutions") boolean allInstitutions, @Param("institutionId") UUID institutionId);

  /** Linhas {cenário, execuções, dispositivos distintos}, mais usados primeiro. */
  @Query("""
      select e.scenarioId, count(e), count(distinct e.deviceId)
      from DeviceEventModel e
      where e.type = :type
        and e.scenarioId is not null
        and e.occurredOn between :from and :to
        and (:allInstitutions = true or e.institutionId = :institutionId)
      group by e.scenarioId
      order by count(e) desc
      """)
  List<Object[]> topScenarios(@Param("type") DeviceEventType type, @Param("from") LocalDate from,
      @Param("to") LocalDate to, @Param("allInstitutions") boolean allInstitutions,
      @Param("institutionId") UUID institutionId, Pageable pageable);

  /** Linhas {dispositivo, execuções, último evento}, mais ativos primeiro. */
  @Query("""
      select e.deviceId, count(e), max(e.occurredAt)
      from DeviceEventModel e
      where e.type = :type
        and e.occurredOn between :from and :to
        and (:allInstitutions = true or e.institutionId = :institutionId)
      group by e.deviceId
      order by count(e) desc
      """)
  List<Object[]> topDevices(@Param("type") DeviceEventType type, @Param("from") LocalDate from,
      @Param("to") LocalDate to, @Param("allInstitutions") boolean allInstitutions,
      @Param("institutionId") UUID institutionId, Pageable pageable);
}
