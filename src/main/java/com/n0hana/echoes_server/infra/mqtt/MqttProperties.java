package com.n0hana.echoes_server.infra.mqtt;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

/**
 * Configuração da conexão MQTT usada no autocadastro de dispositivos.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "mqtt")
public class MqttProperties {

  /** Liga/desliga o cliente MQTT (útil em testes e ambientes sem broker). */
  private boolean enabled = true;

  /** URL do broker. Exemplos: tcp://localhost:1883, ssl://broker:8883. */
  private String brokerUrl = "tcp://localhost:1883";

  /** Identificador único deste cliente no broker. */
  private String clientId = "echoes-server-registration";

  private String username;

  private String password;

  /** Tópico em que os dispositivos publicam o pedido de autocadastro. */
  private String registrationTopic = "register";

  /**
   * Tópico de resposta. Use {@code {hardware_id}} para publicar em um tópico por
   * dispositivo, por exemplo {@code register/ack/{hardware_id}}.
   */
  private String ackTopic = "register/ack/{hardware_id}";

  /** QoS usado na inscrição e nas respostas. */
  private int qos = 1;

  /** Sessão limpa no broker. */
  private boolean cleanSession = true;

  private int connectionTimeoutSeconds = 10;

  private int keepAliveSeconds = 60;

  /** Intervalo entre tentativas quando o broker ainda não está disponível. */
  private int reconnectDelaySeconds = 5;
}
