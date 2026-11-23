package com.n0hana.echoes_server.device.log;

/** Área do firmware que originou o log. */
public enum DeviceLogCategory {
  /** Wi-Fi (STA/AP) e portal de configuração. */
  WIFI,
  /** HTTP/TLS contra a API. */
  NET,
  /** Broker MQTT. */
  MQTT,
  /** Cadastro e chaves do dispositivo. */
  REGISTER,
  /** Cenários, autorização e downloads de áudio. */
  SYNC,
  /** Reprodução de áudio. */
  AUDIO,
  /** Boot, memória e armazenamento. */
  SYSTEM
}
