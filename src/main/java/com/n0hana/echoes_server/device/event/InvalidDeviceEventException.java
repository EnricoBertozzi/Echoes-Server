package com.n0hana.echoes_server.device.event;

/** Evento de uso com combinação inválida de tipo e cenário. */
public class InvalidDeviceEventException extends RuntimeException {

  public InvalidDeviceEventException(String message) {
    super(message);
  }
}
