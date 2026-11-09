package com.n0hana.echoes_server.device.exception;

public class DeviceAuthenticationException extends RuntimeException {

  public DeviceAuthenticationException() {
    super("Credencial do dispositivo inválida");
  }

  public DeviceAuthenticationException(Throwable cause) {
    super("Credencial do dispositivo inválida", cause);
  }
}
