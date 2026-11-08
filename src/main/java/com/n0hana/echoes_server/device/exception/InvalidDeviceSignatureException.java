package com.n0hana.echoes_server.device.exception;

public class InvalidDeviceSignatureException extends RuntimeException {
  public InvalidDeviceSignatureException() {
    super("Assinatura do dispositivo inválida");
  }
}
