package com.n0hana.echoes_server.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.n0hana.echoes_server.dashboard.DashboardDTO.DevicesSummary;
import com.n0hana.echoes_server.dashboard.DashboardDTO.UsageSummary;
import com.n0hana.echoes_server.device.DeviceRepository;
import com.n0hana.echoes_server.device.DeviceScope;
import com.n0hana.echoes_server.device.DeviceStatus;
import com.n0hana.echoes_server.device.event.DeviceEventRepository;
import com.n0hana.echoes_server.device.event.DeviceEventType;
import com.n0hana.echoes_server.device.port.DeviceScopeResolver;
import com.n0hana.echoes_server.institution.InstitutionModel;
import com.n0hana.echoes_server.institution.InstitutionRepository;
import com.n0hana.echoes_server.scenario.ScenarioRepository;
import com.n0hana.echoes_server.user.model.Admin;
import com.n0hana.echoes_server.user.model.Manager;
import com.n0hana.echoes_server.user.model.User;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTests {

  private static final UUID INSTITUTION_A = UUID.randomUUID();
  private static final UUID INSTITUTION_B = UUID.randomUUID();
  private static final UUID REMOVED = UUID.randomUUID();

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
  @DisplayName("Dispositivo ACTIVE em instituição inativa ou removida conta como bloqueado")
  void shouldCountBlockedByInstitution() {
    User admin = new Admin();
    when(scopeResolver.resolve(admin)).thenReturn(new DeviceScope.All());

    when(deviceRepository.countByStatus()).thenReturn(List.of(
        new Object[] { DeviceStatus.PENDING, 1L },
        new Object[] { DeviceStatus.ACTIVE, 4L },
        new Object[] { DeviceStatus.INACTIVE, 1L }));

    when(deviceRepository.countByInstitutionAndStatus()).thenReturn(List.of(
        new Object[] { INSTITUTION_A, DeviceStatus.ACTIVE, 2L },
        new Object[] { INSTITUTION_B, DeviceStatus.ACTIVE, 1L },
        new Object[] { INSTITUTION_B, DeviceStatus.INACTIVE, 1L },
        new Object[] { REMOVED, DeviceStatus.ACTIVE, 1L }));

    when(institutionRepository.findAllById(any())).thenReturn(List.of(
        InstitutionModel.builder().id(INSTITUTION_A).name("A").active(true).build(),
        InstitutionModel.builder().id(INSTITUTION_B).name("B").active(false).build()));

    DevicesSummary summary = service.devices(admin, null);

    assertEquals(1, summary.pending());
    assertEquals(4, summary.active());
    assertEquals(1, summary.inactive());
    assertEquals(2, summary.operating());
    assertEquals(2, summary.blockedByInstitution());
    assertEquals(3, summary.institutions().size());
  }

  @Test
  @DisplayName("Gestor não consulta outra instituição")
  void managerCannotQueryOtherInstitution() {
    User manager = new Manager();
    when(scopeResolver.resolve(manager)).thenReturn(new DeviceScope.InstitutionScope(INSTITUTION_A));

    assertThrows(AccessDeniedException.class, () -> service.devices(manager, INSTITUTION_B));
    assertThrows(AccessDeniedException.class, () -> service.usage(manager, INSTITUTION_B, 30));
  }

  @Test
  @DisplayName("Uso preenche todos os dias do período e compara com o período anterior")
  void shouldFillEveryDayOfThePeriod() {
    User admin = new Admin();
    when(scopeResolver.resolve(admin)).thenReturn(new DeviceScope.All());

    when(eventRepository.countByDayAndType(any(), any(), eq(true), any())).thenReturn(List.of(
        new Object[] { LocalDate.now(), DeviceEventType.PLAY, 5L },
        new Object[] { LocalDate.now(), DeviceEventType.CONNECTION, 2L }));
    when(eventRepository.countByType(eq(DeviceEventType.PLAY), any(), any(), anyBoolean(), any()))
        .thenReturn(2L);
    when(eventRepository.countDistinctDevices(any(), any(), anyBoolean(), any())).thenReturn(3L);

    UsageSummary usage = service.usage(admin, null, 7);

    assertEquals(7, usage.days().size());
    assertEquals(LocalDate.now(), usage.days().get(6).date());
    assertEquals(5, usage.plays());
    assertEquals(2, usage.connections());
    assertEquals(2, usage.previousPlays());
    assertEquals(3, usage.activeDevices());
  }

  @Test
  @DisplayName("Período é limitado entre 1 e 365 dias")
  void shouldClampDays() {
    assertEquals(1, DashboardService.clampDays(0));
    assertEquals(365, DashboardService.clampDays(10_000));
    assertEquals(30, DashboardService.clampDays(30));
  }
}
