package com.n0hana.echoes_server.device.exception;

public class InvalidDevicePublicKeyException extends RuntimeException {
  public InvalidDevicePublicKeyException() {
    super("Chave pública do dispositivo inválida");
  }
}
