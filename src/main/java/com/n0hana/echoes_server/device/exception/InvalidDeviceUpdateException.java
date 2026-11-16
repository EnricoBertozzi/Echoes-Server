package com.n0hana.echoes_server.device.exception;

/** Corpo de atualização de dispositivo inválido ou ambíguo. */
public class InvalidDeviceUpdateException extends RuntimeException {

  public InvalidDeviceUpdateException(String message) {
    super(message);
  }
}
