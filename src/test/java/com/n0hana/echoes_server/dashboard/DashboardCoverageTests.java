package com.n0hana.echoes_server.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

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
import com.n0hana.echoes_server.user.model.Admin;
import com.n0hana.echoes_server.user.model.Manager;
import com.n0hana.echoes_server.user.model.User;

@ExtendWith(MockitoExtension.class)
class DashboardCoverageTests {

  private static final UUID INST_A = UUID.randomUUID();
  private static final UUID INST_B = UUID.randomUUID();

  @Mock
  private DeviceScopeResolver scopeResolver;
  @Mock
  private DeviceRepository deviceRepository;
  @Mock
  private DeviceEventRepository eventRepository;
  @Mock
  private InstitutionRepository institutionRepository;
  @Mock
  private ScenarioRepository scenarioRepository;

  private DashboardService service;

  @BeforeEach
  void setUp() {
    service = new DashboardService(scopeResolver, deviceRepository, eventRepository,
        institutionRepository, scenarioRepository);
  }

  @Test
  @DisplayName("Devices do gestor usa a contagem da própria instituição e ignora outras linhas")
  void devicesForManagerFiltersInstitution() {
    User manager = new Manager();
    when(scopeResolver.resolve(manager)).thenReturn(new DeviceScope.InstitutionScope(INST_A));
    when(deviceRepository.countByStatusInInstitution(INST_A)).thenReturn(List.of(
        new Object[] { DeviceStatus.ACTIVE, 2L },
        new Object[] { DeviceStatus.ACTIVE, 1L }));
    when(deviceRepository.countByInstitutionAndStatus()).thenReturn(List.of(
        new Object[] { null, DeviceStatus.PENDING, 5L },
        new Object[] { INST_B, DeviceStatus.ACTIVE, 9L },
        new Object[] { INST_A, DeviceStatus.ACTIVE, 3L },
        new Object[] { INST_A, DeviceStatus.ACTIVE, 0L }));
    when(institutionRepository.findAllById(any())).thenReturn(List.of(
        InstitutionModel.builder().id(INST_A).name("Alfa").active(true).build()));

    DevicesSummary summary = service.devices(manager, null);

    assertEquals(3, summary.active());
    assertEquals(0, summary.pending());
    assertEquals(0, summary.inactive());
    assertEquals(3, summary.operating());
    assertEquals(0, summary.blockedByInstitution());
    assertEquals(1, summary.institutions().size());
    InstitutionDevices row = summary.institutions().get(0);
    assertEquals("Alfa", row.institutionName());
    assertTrue(row.institutionActive());
    assertEquals(3, row.active());
  }

  @Test
  @DisplayName("Instituições removidas aparecem com nome padrão e ordenadas sem diferenciar maiúsculas")
  void devicesSortsRowsAndNamesRemovedInstitution() {
    User admin = new Admin();
    when(scopeResolver.resolve(admin)).thenReturn(new DeviceScope.All());
    when(deviceRepository.countByStatus()).thenReturn(List.of());
    when(deviceRepository.countByInstitutionAndStatus()).thenReturn(List.of(
        new Object[] { INST_B, DeviceStatus.INACTIVE, 2L },
        new Object[] { INST_A, DeviceStatus.ACTIVE, 1L }));
    when(institutionRepository.findAllById(any())).thenReturn(List.of(
        InstitutionModel.builder().id(INST_A).name("zeta").active(true).build()));

    DevicesSummary summary = service.devices(admin, null);

    assertEquals("Instituição removida", summary.institutions().get(0).institutionName());
    assertFalse(summary.institutions().get(0).institutionActive());
    assertEquals(2, summary.institutions().get(0).inactive());
    assertEquals("zeta", summary.institutions().get(1).institutionName());
    assertEquals(1, summary.operating());
  }

  @Test
  @DisplayName("Uso do gestor filtra pela instituição e zera dias sem eventos")
  void usageForManagerUsesInstitutionFilter() {
    User manager = new Manager();
    when(scopeResolver.resolve(manager)).thenReturn(new DeviceScope.InstitutionScope(INST_A));
    LocalDate today = LocalDate.now();
    when(eventRepository.countByDayAndType(any(), any(), eq(false), eq(INST_A))).thenReturn(List.of(
        new Object[] { today, DeviceEventType.PAUSE, 4L },
        new Object[] { today, DeviceEventType.STOP, 1L },
        new Object[] { today.minusDays(1), DeviceEventType.PLAY, 3L }));
    when(eventRepository.countByType(eq(DeviceEventType.PLAY), any(), any(), eq(false), eq(INST_A)))
        .thenReturn(7L);
    when(eventRepository.countDistinctDevices(any(), any(), eq(false), eq(INST_A))).thenReturn(2L);

    UsageSummary usage = service.usage(manager, INST_A, 3);

    assertEquals(3, usage.days().size());
    assertEquals(today.minusDays(2), usage.from());
    assertEquals(today, usage.to());
    assertEquals(4, usage.pauses());
    assertEquals(1, usage.stops());
    assertEquals(3, usage.plays());
    assertEquals(0, usage.connections());
    assertEquals(7, usage.previousPlays());
    DailyUsage empty = usage.days().get(0);
    assertEquals(0, empty.plays() + empty.pauses() + empty.stops() + empty.connections());
    assertEquals(3, usage.days().get(1).plays());
  }

  @Test
  @DisplayName("Ranking resolve nomes e usa nomes padrão para cenário e dispositivo removidos")
  void rankingsResolvesNamesWithFallbacks() {
    User admin = new Admin();
    when(scopeResolver.resolve(admin)).thenReturn(new DeviceScope.All());
    UUID sc1 = UUID.randomUUID(), sc2 = UUID.randomUUID();
    UUID dv1 = UUID.randomUUID(), dv2 = UUID.randomUUID();
    LocalDateTime last = LocalDateTime.of(2025, 1, 2, 3, 4);

    when(eventRepository.topScenarios(eq(DeviceEventType.PLAY), any(), any(), eq(true), any(), any(Pageable.class)))
        .thenReturn(List.of(new Object[] { sc1, 5L, 2L }, new Object[] { sc2, 1L, 1L }));
    when(eventRepository.topDevices(eq(DeviceEventType.PLAY), any(), any(), eq(true), any(), any(Pageable.class)))
        .thenReturn(List.of(new Object[] { dv1, 4L, last }, new Object[] { dv2, 2L, last }));
    when(scenarioRepository.findAllById(any())).thenReturn(List.of(
        ScenarioModel.builder().id(sc1).name("Sopro").build()));
    DeviceModel device = new DeviceModel();
    device.setId(dv1);
    device.setName("Estetoscópio 1");
    when(deviceRepository.findAllById(any())).thenReturn(List.of(device));

    Rankings rankings = service.rankings(admin, null, 30);

    List<ScenarioUsage> scenarios = rankings.scenarios();
    assertEquals("Sopro", scenarios.get(0).name());
    assertEquals(5, scenarios.get(0).plays());
    assertEquals(2, scenarios.get(0).devices());
    assertEquals("Cenário removido", scenarios.get(1).name());
    List<DeviceUsage> devices = rankings.devices();
    assertEquals("Estetoscópio 1", devices.get(0).name());
    assertEquals(last, devices.get(0).lastEventAt());
    assertEquals("Dispositivo removido", devices.get(1).name());
  }

  @Test
  @DisplayName("Ranking do gestor consulta com o filtro da instituição")
  void rankingsForManagerUsesFilter() {
    User manager = new Manager();
    when(scopeResolver.resolve(manager)).thenReturn(new DeviceScope.InstitutionScope(INST_A));
    when(eventRepository.topScenarios(any(), any(), any(), eq(false), eq(INST_A), any())).thenReturn(List.of());
    when(eventRepository.topDevices(any(), any(), any(), eq(false), eq(INST_A), any())).thenReturn(List.of());

    Rankings rankings = service.rankings(manager, null, 99999);

    assertTrue(rankings.scenarios().isEmpty());
    assertTrue(rankings.devices().isEmpty());
  }

  @Test
  @DisplayName("Controller delega ao service e devolve 200")
  void controllerDelegates() {
    DashboardService mocked = mock(DashboardService.class);
    DashboardController controller = new DashboardController(mocked);
    User user = new Admin();
    UUID inst = UUID.randomUUID();
    DevicesSummary devices = new DevicesSummary(0, 0, 0, 0, 0, List.of());
    UsageSummary usage = new UsageSummary(LocalDate.now(), LocalDate.now(), 0, 0, 0, 0, 0, 0, List.of());
    Rankings rankings = new Rankings(List.of(), List.of());
    when(mocked.devices(user, inst)).thenReturn(devices);
    when(mocked.usage(user, inst, 7)).thenReturn(usage);
    when(mocked.rankings(user, inst, 14)).thenReturn(rankings);

    ResponseEntity<DevicesSummary> r1 = controller.devices(user, inst);
    ResponseEntity<UsageSummary> r2 = controller.usage(user, inst, 7);
    ResponseEntity<Rankings> r3 = controller.rankings(user, inst, 14);

    assertEquals(200, r1.getStatusCode().value());
    assertSame(devices, r1.getBody());
    assertSame(usage, r2.getBody());
    assertSame(rankings, r3.getBody());
    verify(mocked).usage(user, inst, 7);
  }
}
