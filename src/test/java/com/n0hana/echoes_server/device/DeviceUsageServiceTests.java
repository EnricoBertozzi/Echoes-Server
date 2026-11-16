package com.n0hana.echoes_server.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.n0hana.echoes_server.device.event.DeviceEventRequest;
import com.n0hana.echoes_server.device.event.DeviceEventType;
import com.n0hana.echoes_server.device.event.DeviceUsageEvent;
import com.n0hana.echoes_server.device.event.DeviceUsageService;
import com.n0hana.echoes_server.device.event.InvalidDeviceEventException;
import com.n0hana.echoes_server.device.port.DeviceUsageRecorder;
import com.n0hana.echoes_server.institution.InstitutionModel;
import com.n0hana.echoes_server.scenario.ScenarioService;

@ExtendWith(MockitoExtension.class)
class DeviceUsageServiceTests {

  private static final UUID DEVICE_ID = UUID.randomUUID();
  private static final UUID INSTITUTION_ID = UUID.randomUUID();
  private static final UUID SCENARIO_ID = UUID.randomUUID();

  @Mock
  private DeviceUsageRecorder recorder;

  @Mock
  private ScenarioService scenarioService;

  private DeviceUsageService service;
  private DeviceModel device;

  @BeforeEach
  void setUp() {
    service = new DeviceUsageService(recorder, scenarioService);
    device = DeviceModel.builder()
        .id(DEVICE_ID)
        .hardwareId("ESP-1")
        .publicKey("pem")
        .status(DeviceStatus.ACTIVE)
        .institution(InstitutionModel.builder().id(INSTITUTION_ID).build())
        .build();
  }

  @Test
  @DisplayName("CONNECTION é gravado sem cenário, com a instituição do dispositivo")
  void shouldRecordConnection() {
    service.report(device, new DeviceEventRequest(DeviceEventType.CONNECTION, null));

    ArgumentCaptor<DeviceUsageEvent> captor = ArgumentCaptor.forClass(DeviceUsageEvent.class);
    verify(recorder).record(captor.capture());

    DeviceUsageEvent event = captor.getValue();
    assertEquals(DEVICE_ID, event.deviceId());
    assertEquals(INSTITUTION_ID, event.institutionId());
    assertEquals(DeviceEventType.CONNECTION, event.type());
    assertNull(event.scenarioId());
    assertNotNull(event.occurredAt());
  }

  @Test
  @DisplayName("PLAY exige cenário e confere se ele é do dispositivo")
  void shouldRecordPlayWithScenario() {
    service.report(device, new DeviceEventRequest(DeviceEventType.PLAY, SCENARIO_ID));

    verify(scenarioService).findScenarioById(SCENARIO_ID, device);
    verify(recorder).record(any(DeviceUsageEvent.class));
  }

  @Test
  @DisplayName("PLAY sem cenário é recusado")
  void shouldRejectPlayWithoutScenario() {
    assertThrows(InvalidDeviceEventException.class,
        () -> service.report(device, new DeviceEventRequest(DeviceEventType.PLAY, null)));

    verify(recorder, never()).record(any(DeviceUsageEvent.class));
  }

  @Test
  @DisplayName("CONNECTION com cenário é recusado")
  void shouldRejectConnectionWithScenario() {
    assertThrows(InvalidDeviceEventException.class,
        () -> service.report(device, new DeviceEventRequest(DeviceEventType.CONNECTION, SCENARIO_ID)));

    verify(recorder, never()).record(any(DeviceUsageEvent.class));
  }

  @Test
  @DisplayName("Dispositivo sem instituição não reporta uso")
  void shouldRejectDeviceWithoutInstitution() {
    device.setInstitution(null);

    assertThrows(AccessDeniedException.class,
        () -> service.report(device, new DeviceEventRequest(DeviceEventType.CONNECTION, null)));
  }
}
