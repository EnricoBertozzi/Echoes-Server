package com.n0hana.echoes_server.infra.mqtt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.n0hana.echoes_server.device.DeviceModel;
import com.n0hana.echoes_server.device.DeviceRepository;
import com.n0hana.echoes_server.device.DeviceStatus;
import com.n0hana.echoes_server.device.DeviceStatusChanged;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class DeviceStatusMqttNotifierTests {

  private static final UUID DEVICE_ID = UUID.randomUUID();

  @Mock
  private DeviceRegistrationMqttClient mqttClient;
  @Mock
  private DeviceRepository deviceRepository;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private MqttProperties properties;
  private DeviceStatusMqttNotifier notifier;

  @BeforeEach
  void setUp() {
    properties = new MqttProperties();
    notifier = new DeviceStatusMqttNotifier(mqttClient, deviceRepository, properties, objectMapper);
  }

  private DeviceModel device(String hardwareId) {
    DeviceModel device = org.mockito.Mockito.mock(DeviceModel.class);
    when(device.getHardwareId()).thenReturn(hardwareId);
    lenient().when(device.getId()).thenReturn(DEVICE_ID);
    return device;
  }

  @Test
  @DisplayName("Publica o status retido no tópico do dispositivo")
  void publishesRetainedStatus() throws Exception {
    DeviceModel device = device("AA:BB:CC:DD:EE:FF");
    when(deviceRepository.findById(DEVICE_ID)).thenReturn(Optional.of(device));
    when(mqttClient.publish(anyString(), any(), anyBoolean())).thenReturn(true);

    notifier.onStatusChanged(new DeviceStatusChanged(DEVICE_ID, DeviceStatus.ACTIVE));

    ArgumentCaptor<String> topic = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<byte[]> body = ArgumentCaptor.forClass(byte[].class);
    ArgumentCaptor<Boolean> retained = ArgumentCaptor.forClass(Boolean.class);
    verify(mqttClient).publish(topic.capture(), body.capture(), retained.capture());

    assertEquals("device/status/AA:BB:CC:DD:EE:FF", topic.getValue());
    assertTrue(retained.getValue());
    JsonNode json = objectMapper.readTree(new String(body.getValue(), StandardCharsets.UTF_8));
    assertEquals("ACTIVE", json.get("deviceStatus").asString());
    assertEquals("AA:BB:CC:DD:EE:FF", json.get("hardware_id").asString());
    assertEquals(DEVICE_ID.toString(), json.get("deviceId").asString());
  }

  @Test
  @DisplayName("hardware_id malformado não cria níveis de tópico")
  void sanitizesHardwareId() {
    assertEquals("device/status/a_b_c", notifier.topicFor("a/b#c"));
  }

  @Test
  @DisplayName("Não publica com MQTT desligado")
  void doesNothingWhenDisabled() {
    properties.setEnabled(false);

    notifier.onStatusChanged(new DeviceStatusChanged(DEVICE_ID, DeviceStatus.INACTIVE));

    verify(mqttClient, never()).publish(anyString(), any(), anyBoolean());
  }

  @Test
  @DisplayName("Falha do broker não propaga exceção")
  void brokerFailureDoesNotPropagate() {
    DeviceModel device = device("AA:BB");
    when(deviceRepository.findById(DEVICE_ID)).thenReturn(Optional.of(device));
    when(mqttClient.publish(anyString(), any(), anyBoolean())).thenThrow(new IllegalStateException("broker"));

    notifier.onStatusChanged(new DeviceStatusChanged(DEVICE_ID, DeviceStatus.ACTIVE));
  }

  @Test
  @DisplayName("Dispositivo inexistente é ignorado")
  void ignoresUnknownDevice() {
    when(deviceRepository.findById(DEVICE_ID)).thenReturn(Optional.empty());

    notifier.onStatusChanged(new DeviceStatusChanged(DEVICE_ID, DeviceStatus.ACTIVE));

    verify(mqttClient, never()).publish(anyString(), any(), anyBoolean());
  }

  @Test
  @DisplayName("O ouvinte só dispara depois do commit")
  void listensAfterCommit() throws Exception {
    Method method = DeviceStatusMqttNotifier.class.getMethod("onStatusChanged", DeviceStatusChanged.class);
    TransactionalEventListener annotation = method.getAnnotation(TransactionalEventListener.class);

    assertEquals(TransactionPhase.AFTER_COMMIT, annotation.phase());
  }
}
