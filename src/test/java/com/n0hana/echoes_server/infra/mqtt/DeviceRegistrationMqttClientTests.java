package com.n0hana.echoes_server.infra.mqtt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DeviceRegistrationMqttClientTests {

  @Test
  @DisplayName("Cada instância conecta com um client id próprio, a partir do prefixo configurado")
  void clientIdIsUniquePerInstance() {
    String first = DeviceRegistrationMqttClient.instanceClientId("echoes-server");
    String second = DeviceRegistrationMqttClient.instanceClientId("echoes-server");

    assertTrue(first.startsWith("echoes-server-"));
    assertNotEquals(first, second);
    // dentro do limite de 23 caracteres que todo broker MQTT 3.1.1 aceita
    assertTrue(first.length() <= 23);
  }

  @Test
  @DisplayName("Sem prefixo configurado, usa echoes-server")
  void blankClientIdFallsBackToDefault() {
    assertTrue(DeviceRegistrationMqttClient.instanceClientId("  ").startsWith("echoes-server-"));
  }

  @Test
  @DisplayName("hardware_id malformado não cria níveis de tópico")
  void topicSegmentIsSanitized() {
    assertEquals("AA:BB_CC_", DeviceRegistrationMqttClient.topicSegment("AA:BB/CC#"));
  }
}
