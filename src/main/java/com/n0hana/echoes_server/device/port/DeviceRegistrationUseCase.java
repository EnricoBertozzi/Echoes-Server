package com.n0hana.echoes_server.device.port;

import com.n0hana.echoes_server.device.DeviceRegistrationPayload;
import com.n0hana.echoes_server.device.DeviceRegistrationResult;

/**
 * Porta de entrada do cadastro, independente do transporte (MQTT hoje, HTTP
 * se for preciso). O dispositivo novo sempre nasce PENDING.
 */
public interface DeviceRegistrationUseCase {

  DeviceRegistrationResult register(DeviceRegistrationPayload payload);
}
