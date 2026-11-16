package com.n0hana.echoes_server.device.exception;

/** Transição de status não permitida para o estado atual do dispositivo. */
public class InvalidDeviceStateException extends RuntimeException {

  public InvalidDeviceStateException(String message) {
    super(message);
  }
}
