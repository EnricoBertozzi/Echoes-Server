package com.n0hana.echoes_server.infra.mqtt;

import org.springframework.stereotype.Component;

import com.n0hana.echoes_server.device.DeviceRegistrationPayload;
import com.n0hana.echoes_server.device.DeviceRegistrationResult;
import com.n0hana.echoes_server.device.DeviceRegistrationService;
import com.n0hana.echoes_server.device.exception.InvalidDevicePublicKeyException;
import com.n0hana.echoes_server.device.exception.InvalidDeviceRegistrationException;
import com.n0hana.echoes_server.device.exception.InvalidDeviceSignatureException;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class DeviceRegistrationMessageHandler {

  private final ObjectMapper objectMapper;
  private final DeviceRegistrationService registrationService;

  public DeviceRegistrationResult handle(String payload) {
    DeviceRegistrationPayload request;

    try {
      request = objectMapper.readValue(payload, DeviceRegistrationPayload.class);
    } catch (Exception exception) {
      return DeviceRegistrationResult.rejected(null, "Payload de registro inválido");
    }

    if (request == null)
      return DeviceRegistrationResult.rejected(null, "Payload de registro inválido");

    try {
      return registrationService.register(request);
    } catch (InvalidDeviceRegistrationException
        | InvalidDevicePublicKeyException
        | InvalidDeviceSignatureException exception) {
      return DeviceRegistrationResult.rejected(request.mac(), exception.getMessage());
    } catch (Exception exception) {
      return DeviceRegistrationResult.rejected(
          request.mac(),
          "Erro interno ao registrar dispositivo");
    }
  }

  public String toJson(DeviceRegistrationResult result) throws Exception {
    return objectMapper.writeValueAsString(result);
  }
}
