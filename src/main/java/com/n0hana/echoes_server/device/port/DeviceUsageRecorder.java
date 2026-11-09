package com.n0hana.echoes_server.device.port;

import com.n0hana.echoes_server.device.event.DeviceUsageEvent;

/**
 * Porta: grava um evento de uso. Hoje vai para o banco relacional; um banco de
 * séries temporais pode substituir a implementação sem tocar no domínio.
 */
public interface DeviceUsageRecorder {

  void record(DeviceUsageEvent event);
}
