package com.n0hana.echoes_server.infra.mqtt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MqttPropertiesCoverageTests {

  @Test
  @DisplayName("Valores padrão")
  void defaults() {
    MqttProperties p = new MqttProperties();

    assertTrue(p.isEnabled());
    assertEquals("tcp://localhost:1883", p.getBrokerUrl());
    assertEquals("echoes-server-registration", p.getClientId());
    assertNull(p.getUsername());
    assertNull(p.getPassword());
    assertEquals("register", p.getRegistrationTopic());
    assertEquals("register/ack/{hardware_id}", p.getAckTopic());
    assertEquals("device/status/{hardware_id}", p.getDeviceStatusTopic());
    assertEquals(1, p.getQos());
    assertTrue(p.isCleanSession());
    assertEquals(10, p.getConnectionTimeoutSeconds());
    assertEquals(60, p.getKeepAliveSeconds());
    assertEquals(5, p.getReconnectDelaySeconds());
  }

  @Test
  @DisplayName("Setters alteram todos os campos")
  void setters() {
    MqttProperties p = new MqttProperties();
    p.setEnabled(false);
    p.setBrokerUrl("ssl://broker:8883");
    p.setClientId("c");
    p.setUsername("u");
    p.setPassword("pw");
    p.setRegistrationTopic("r");
    p.setAckTopic("a");
    p.setDeviceStatusTopic("s");
    p.setQos(2);
    p.setCleanSession(false);
    p.setConnectionTimeoutSeconds(1);
    p.setKeepAliveSeconds(2);
    p.setReconnectDelaySeconds(3);

    assertFalse(p.isEnabled());
    assertEquals("ssl://broker:8883", p.getBrokerUrl());
    assertEquals("c", p.getClientId());
    assertEquals("u", p.getUsername());
    assertEquals("pw", p.getPassword());
    assertEquals("r", p.getRegistrationTopic());
    assertEquals("a", p.getAckTopic());
    assertEquals("s", p.getDeviceStatusTopic());
    assertEquals(2, p.getQos());
    assertFalse(p.isCleanSession());
    assertEquals(1, p.getConnectionTimeoutSeconds());
    assertEquals(2, p.getKeepAliveSeconds());
    assertEquals(3, p.getReconnectDelaySeconds());
  }
}
