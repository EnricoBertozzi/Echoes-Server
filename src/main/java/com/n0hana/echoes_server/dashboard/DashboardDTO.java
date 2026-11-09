package com.n0hana.echoes_server.dashboard;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Respostas da dashboard. */
public class DashboardDTO {

  /**
   * Situação dos dispositivos.
   *
   * @param pending              aguardando aprovação (sem instituição; só o ADMIN vê)
   * @param active               ACTIVE no banco
   * @param inactive             INACTIVE no banco
   * @param operating            ACTIVE em instituição existente e ativa (pode operar de fato)
   * @param blockedByInstitution ACTIVE que não opera porque a instituição está inativa ou removida
   */
  public record DevicesSummary(
      long pending,
      long active,
      long inactive,
      long operating,
      long blockedByInstitution,
      List<InstitutionDevices> institutions) {
  }

  public record InstitutionDevices(
      UUID institutionId,
      String institutionName,
      boolean institutionActive,
      long active,
      long inactive,
      long operating) {
  }

  /** Uso no período; {@code previousPlays} é o período anterior, de mesmo tamanho. */
  public record UsageSummary(
      LocalDate from,
      LocalDate to,
      long connections,
      long plays,
      long pauses,
      long stops,
      long previousPlays,
      long activeDevices,
      List<DailyUsage> days) {
  }

  public record DailyUsage(LocalDate date, long connections, long plays, long pauses, long stops) {
  }

  public record Rankings(List<ScenarioUsage> scenarios, List<DeviceUsage> devices) {
  }

  public record ScenarioUsage(UUID scenarioId, String name, long plays, long devices) {
  }

  public record DeviceUsage(UUID deviceId, String name, long plays, LocalDateTime lastEventAt) {
  }
}
