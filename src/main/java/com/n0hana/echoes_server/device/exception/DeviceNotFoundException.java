package com.n0hana.echoes_server.device.exception;

public class DeviceNotFoundException extends RuntimeException {
  public DeviceNotFoundException() {
    super("Dispositivo não encontrado");
  }
}
