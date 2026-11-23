package com.n0hana.echoes_server.infra.mqtt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.n0hana.echoes_server.device.DeviceModel;
import com.n0hana.echoes_server.device.DeviceRepository;
import com.n0hana.echoes_server.device.DeviceStatus;
import com.n0hana.echoes_server.device.DeviceStatusChanged;

import tools.jackson.databind.ObjectMapper;

class DeviceStatusMqttNotifierCoverageTests {

  private static final UUID DEVICE_ID = UUID.randomUUID();

  private final DeviceRegistrationMqttClient mqttClient = mock(DeviceRegistrationMqttClient.class);
  private final DeviceRepository repository = mock(DeviceRepository.class);
  private final MqttProperties properties = new MqttProperties();
  private final DeviceStatusMqttNotifier notifier = new DeviceStatusMqttNotifier(mqttClient, repository, properties,
      new ObjectMapper());

  @Test
  @DisplayName("Dispositivo sem hardware_id é ignorado")
  void ignoresDeviceWithoutHardwareId() {
    DeviceModel device = mock(DeviceModel.class);
    when(device.getHardwareId()).thenReturn(null);
    when(repository.findById(DEVICE_ID)).thenReturn(Optional.of(device));

    notifier.onStatusChanged(new DeviceStatusChanged(DEVICE_ID, DeviceStatus.ACTIVE));

    verify(mqttClient, never()).publish(anyString(), any(byte[].class), anyBoolean());
  }

  @Test
  @DisplayName("Broker indisponível (publish false) não gera exceção")
  void brokerUnavailableOnlyLogs() {
    DeviceModel device = mock(DeviceModel.class);
    when(device.getHardwareId()).thenReturn("AA:BB");
    when(device.getId()).thenReturn(DEVICE_ID);
    when(repository.findById(DEVICE_ID)).thenReturn(Optional.of(device));
    when(mqttClient.publish(anyString(), any(byte[].class), anyBoolean())).thenReturn(false);

    notifier.onStatusChanged(new DeviceStatusChanged(DEVICE_ID, DeviceStatus.ACTIVE));

    verify(mqttClient).publish(anyString(), any(byte[].class), anyBoolean());
  }

  @Test
  @DisplayName("Tópico aceita {hardwareId} e {hardware_id}")
  void topicSupportsBothPlaceholders() {
    properties.setDeviceStatusTopic("s/{hardwareId}/{hardware_id}");

    assertEquals("s/AA:BB/AA:BB", notifier.topicFor("AA:BB"));
  }
}
