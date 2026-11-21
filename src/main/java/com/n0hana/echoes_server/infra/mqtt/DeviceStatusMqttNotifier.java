package com.n0hana.echoes_server.infra.mqtt;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.n0hana.echoes_server.device.DeviceModel;
import com.n0hana.echoes_server.device.DeviceRepository;
import com.n0hana.echoes_server.device.DeviceStatusChanged;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * RF44: avisa o dispositivo, por MQTT, quando o status dele muda.
 *
 * <p>
 * Escuta {@link DeviceStatusChanged} só depois do commit (o aviso não pode
 * anunciar um estado que sofreu rollback) e publica de forma retida em
 * {@code device/status/{hardware_id}}. Retida porque o firmware só mantém a
 * conexão aberta no autocadastro: ao se inscrever de novo, recebe o último
 * estado. Falha de broker nunca afeta a operação do admin; o dispositivo
 * continua podendo consultar o status por HTTP.
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceStatusMqttNotifier {

  private final DeviceRegistrationMqttClient mqttClient;
  private final DeviceRepository deviceRepository;
  private final MqttProperties properties;
  private final ObjectMapper objectMapper;

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onStatusChanged(DeviceStatusChanged event) {
    if (!properties.isEnabled())
      return;

    try {
      DeviceModel device = deviceRepository.findById(event.deviceId()).orElse(null);

      if (device == null || device.getHardwareId() == null)
        return;

      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("hardware_id", device.getHardwareId());
      payload.put("deviceId", device.getId());
      payload.put("deviceStatus", event.status());

      boolean sent = mqttClient.publish(
          topicFor(device.getHardwareId()),
          objectMapper.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8),
          true);

      if (!sent)
        log.warn("Aviso de status do dispositivo {} não publicado (broker indisponível)", event.deviceId());
    } catch (Exception exception) {
      log.warn("Falha ao avisar mudança de status do dispositivo {}: {}", event.deviceId(),
          exception.getClass().getSimpleName());
    }
  }

  String topicFor(String hardwareId) {
    String segment = DeviceRegistrationMqttClient.topicSegment(hardwareId);
    return properties.getDeviceStatusTopic()
        .replace("{hardware_id}", segment)
        .replace("{hardwareId}", segment);
  }
}
