package com.n0hana.echoes_server.device.port;

import com.n0hana.echoes_server.device.DeviceModel;

/**
 * Porta: responde "este dispositivo pode operar agora?". É o único lugar que
 * combina o status do dispositivo com o da instituição.
 */
public interface DeviceAccessPolicy {

  boolean canOperate(DeviceModel device);
}
