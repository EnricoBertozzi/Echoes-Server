package com.n0hana.echoes_server.dashboard;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.n0hana.echoes_server.dashboard.DashboardDTO.DailyUsage;
import com.n0hana.echoes_server.dashboard.DashboardDTO.DeviceUsage;
import com.n0hana.echoes_server.dashboard.DashboardDTO.DevicesSummary;
import com.n0hana.echoes_server.dashboard.DashboardDTO.InstitutionDevices;
import com.n0hana.echoes_server.dashboard.DashboardDTO.Rankings;
import com.n0hana.echoes_server.dashboard.DashboardDTO.ScenarioUsage;
import com.n0hana.echoes_server.dashboard.DashboardDTO.UsageSummary;
import com.n0hana.echoes_server.device.DeviceModel;
import com.n0hana.echoes_server.device.DeviceRepository;
import com.n0hana.echoes_server.device.DeviceScope;
import com.n0hana.echoes_server.device.DeviceStatus;
import com.n0hana.echoes_server.device.event.DeviceEventRepository;
import com.n0hana.echoes_server.device.event.DeviceEventType;
import com.n0hana.echoes_server.device.port.DeviceScopeResolver;
import com.n0hana.echoes_server.institution.InstitutionModel;
import com.n0hana.echoes_server.institution.InstitutionRepository;
import com.n0hana.echoes_server.scenario.ScenarioModel;
import com.n0hana.echoes_server.scenario.ScenarioRepository;
import com.n0hana.echoes_server.user.model.User;

import lombok.RequiredArgsConstructor;

/**
 * Consultas da dashboard.
 *
 * <p>
 * Escopo: ADMIN vê tudo e pode filtrar por instituição; gestor vê só a própria
 * instituição (pedir outra dá 403). "Operando" segue a mesma regra de
 * {@code DeviceAccessPolicy}: ACTIVE e instituição existente e ativa. Uma
 * instituição removida (soft delete) não é carregada, então seus dispositivos
 * entram como bloqueados.
 * </p>
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

  static final int MAX_DAYS = 365;
  static final int RANKING_SIZE = 10;
  private static final UUID NO_INSTITUTION = new UUID(0L, 0L);

  private final DeviceScopeResolver scopeResolver;
  private final DeviceRepository deviceRepository;
  private final DeviceEventRepository eventRepository;
  private final InstitutionRepository institutionRepository;
  private final ScenarioRepository scenarioRepository;

  @Transactional(readOnly = true)
  public DevicesSummary devices(User user, UUID requestedInstitutionId) {
    UUID institutionId = effectiveInstitution(user, requestedInstitutionId);

    Map<DeviceStatus, Long> totals = toStatusMap(institutionId == null
        ? deviceRepository.countByStatus()
        : deviceRepository.countByStatusInInstitution(institutionId));

    Map<UUID, Map<DeviceStatus, Long>> perInstitution = new LinkedHashMap<>();
    for (Object[] row : deviceRepository.countByInstitutionAndStatus()) {
      UUID id = (UUID) row[0];
      if (id == null || (institutionId != null && !institutionId.equals(id)))
        continue;
      perInstitution
          .computeIfAbsent(id, key -> new EnumMap<>(DeviceStatus.class))
          .merge((DeviceStatus) row[1], (Long) row[2], Long::sum);
    }

    // Instituição removida (soft delete) não volta aqui: fica de fora e seus ACTIVE contam como bloqueados.
    Map<UUID, InstitutionModel> institutions = institutionRepository.findAllById(perInstitution.keySet())
        .stream().collect(Collectors.toMap(InstitutionModel::getId, Function.identity()));

    List<InstitutionDevices> rows = new ArrayList<>();
    long operating = 0;
    for (Map.Entry<UUID, Map<DeviceStatus, Long>> entry : perInstitution.entrySet()) {
      InstitutionModel institution = institutions.get(entry.getKey());
      long active = entry.getValue().getOrDefault(DeviceStatus.ACTIVE, 0L);
      long inactive = entry.getValue().getOrDefault(DeviceStatus.INACTIVE, 0L);

      boolean institutionActive = institution != null && institution.isActive();
      long rowOperating = institutionActive ? active : 0;
      operating += rowOperating;

      rows.add(new InstitutionDevices(
          entry.getKey(),
          institution != null ? institution.getName() : "Instituição removida",
          institutionActive,
          active,
          inactive,
          rowOperating));
    }
    rows.sort(Comparator.comparing(InstitutionDevices::institutionName, String.CASE_INSENSITIVE_ORDER));

    long active = totals.getOrDefault(DeviceStatus.ACTIVE, 0L);

    return new DevicesSummary(
        totals.getOrDefault(DeviceStatus.PENDING, 0L),
        active,
        totals.getOrDefault(DeviceStatus.INACTIVE, 0L),
        operating,
        active - operating,
        rows);
  }

  @Transactional(readOnly = true)
  public UsageSummary usage(User user, UUID requestedInstitutionId, int days) {
    UUID institutionId = effectiveInstitution(user, requestedInstitutionId);
    boolean all = institutionId == null;
    UUID filter = all ? NO_INSTITUTION : institutionId;

    int size = clampDays(days);
    LocalDate to = LocalDate.now();
    LocalDate from = to.minusDays(size - 1L);
    LocalDate previousTo = from.minusDays(1);
    LocalDate previousFrom = previousTo.minusDays(size - 1L);

    Map<LocalDate, Map<DeviceEventType, Long>> byDay = new HashMap<>();
    for (Object[] row : eventRepository.countByDayAndType(from, to, all, filter)) {
      byDay.computeIfAbsent((LocalDate) row[0], key -> new EnumMap<>(DeviceEventType.class))
          .put((DeviceEventType) row[1], (Long) row[2]);
    }

    List<DailyUsage> series = new ArrayList<>(size);
    long connections = 0, plays = 0, pauses = 0, stops = 0;

    for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
      Map<DeviceEventType, Long> counts = byDay.getOrDefault(day, Map.of());
      long c = counts.getOrDefault(DeviceEventType.CONNECTION, 0L);
      long p = counts.getOrDefault(DeviceEventType.PLAY, 0L);
      long pa = counts.getOrDefault(DeviceEventType.PAUSE, 0L);
      long s = counts.getOrDefault(DeviceEventType.STOP, 0L);

      connections += c;
      plays += p;
      pauses += pa;
      stops += s;
      series.add(new DailyUsage(day, c, p, pa, s));
    }

    return new UsageSummary(
        from,
        to,
        connections,
        plays,
        pauses,
        stops,
        eventRepository.countByType(DeviceEventType.PLAY, previousFrom, previousTo, all, filter),
        eventRepository.countDistinctDevices(from, to, all, filter),
        series);
  }

  @Transactional(readOnly = true)
  public Rankings rankings(User user, UUID requestedInstitutionId, int days) {
    UUID institutionId = effectiveInstitution(user, requestedInstitutionId);
    boolean all = institutionId == null;
    UUID filter = all ? NO_INSTITUTION : institutionId;

    LocalDate to = LocalDate.now();
    LocalDate from = to.minusDays(clampDays(days) - 1L);
    PageRequest top = PageRequest.of(0, RANKING_SIZE);

    List<Object[]> scenarioRows = eventRepository.topScenarios(DeviceEventType.PLAY, from, to, all, filter, top);
    List<Object[]> deviceRows = eventRepository.topDevices(DeviceEventType.PLAY, from, to, all, filter, top);

    Set<UUID> scenarioIds = scenarioRows.stream().map(row -> (UUID) row[0]).collect(Collectors.toSet());
    Map<UUID, String> scenarioNames = scenarioRepository.findAllById(scenarioIds).stream()
        .collect(Collectors.toMap(ScenarioModel::getId, ScenarioModel::getName));

    Set<UUID> deviceIds = deviceRows.stream().map(row -> (UUID) row[0]).collect(Collectors.toSet());
    Map<UUID, String> deviceNames = deviceRepository.findAllById(deviceIds).stream()
        .collect(Collectors.toMap(DeviceModel::getId, DeviceModel::getName));

    List<ScenarioUsage> scenarios = scenarioRows.stream()
        .map(row -> new ScenarioUsage(
            (UUID) row[0],
            scenarioNames.getOrDefault((UUID) row[0], "Cenário removido"),
            (Long) row[1],
            (Long) row[2]))
        .toList();

    List<DeviceUsage> devices = deviceRows.stream()
        .map(row -> new DeviceUsage(
            (UUID) row[0],
            deviceNames.getOrDefault((UUID) row[0], "Dispositivo removido"),
            (Long) row[1],
            (LocalDateTime) row[2]))
        .toList();

    return new Rankings(scenarios, devices);
  }

  private UUID effectiveInstitution(User user, UUID requested) {
    DeviceScope scope = scopeResolver.resolve(user);
    return scope.effectiveInstitution(requested);
  }

  private Map<DeviceStatus, Long> toStatusMap(List<Object[]> rows) {
    Map<DeviceStatus, Long> map = new EnumMap<>(DeviceStatus.class);
    for (Object[] row : rows)
      map.merge((DeviceStatus) row[0], (Long) row[1], Long::sum);
    return map;
  }

  static int clampDays(int days) {
    return Math.max(1, Math.min(days, MAX_DAYS));
  }
}
