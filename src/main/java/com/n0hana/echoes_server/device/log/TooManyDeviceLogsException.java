package com.n0hana.echoes_server.device.log;

/** O dispositivo enviou lotes de log rápido demais. */
public class TooManyDeviceLogsException extends RuntimeException {

  public TooManyDeviceLogsException() {
    super("Muitos lotes de log; tente novamente mais tarde");
  }
}
