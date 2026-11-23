package com.n0hana.echoes_server.infra.mqtt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.n0hana.echoes_server.device.DeviceRegistrationPayload;
import com.n0hana.echoes_server.device.DeviceRegistrationResult;
import com.n0hana.echoes_server.device.DeviceRegistrationResult.Status;
import com.n0hana.echoes_server.device.exception.InvalidDevicePublicKeyException;
import com.n0hana.echoes_server.device.exception.InvalidDeviceRegistrationException;
import com.n0hana.echoes_server.device.port.DeviceRegistrationUseCase;

import tools.jackson.databind.ObjectMapper;

class DeviceRegistrationMessageHandlerCoverageTests {

  private static final String PAYLOAD = "{\"hardware_id\":\"AA:BB:CC:DD:EE:FF\",\"public_key\":\"pem\",\"signature\":\"sig\"}";

  private final DeviceRegistrationUseCase useCase = mock(DeviceRegistrationUseCase.class);
  private final DeviceRegistrationMessageHandler handler = new DeviceRegistrationMessageHandler(new ObjectMapper(),
      useCase);

  @Test
  @DisplayName("Payload JSON null é rejeitado sem chamar o caso de uso")
  void nullJsonIsRejected() {
    DeviceRegistrationResult result = handler.handle("null");

    assertEquals(Status.REJECTED, result.status());
    assertNull(result.hardwareId());
    assertEquals("Payload de registro inválido", result.message());
    verify(useCase, never()).register(any());
  }

  @Test
  @DisplayName("Registro inválido vira rejeição com a mensagem da exceção e o hardware_id")
  void invalidRegistrationIsRejected() {
    when(useCase.register(any(DeviceRegistrationPayload.class)))
        .thenThrow(new InvalidDeviceRegistrationException("hardware_id obrigatório"));

    DeviceRegistrationResult result = handler.handle(PAYLOAD);

    assertEquals(Status.REJECTED, result.status());
    assertEquals("AA:BB:CC:DD:EE:FF", result.hardwareId());
    assertEquals("hardware_id obrigatório", result.message());
  }

  @Test
  @DisplayName("Chave pública inválida vira rejeição")
  void invalidPublicKeyIsRejected() {
    when(useCase.register(any(DeviceRegistrationPayload.class)))
        .thenThrow(new InvalidDevicePublicKeyException());

    DeviceRegistrationResult result = handler.handle(PAYLOAD);

    assertEquals(Status.REJECTED, result.status());
    assertEquals("Chave pública do dispositivo inválida", result.message());
  }

  @Test
  @DisplayName("Erro inesperado vira rejeição genérica sem vazar detalhes")
  void unexpectedErrorIsRejectedGenerically() {
    when(useCase.register(any(DeviceRegistrationPayload.class)))
        .thenThrow(new IllegalStateException("detalhe interno"));

    DeviceRegistrationResult result = handler.handle(PAYLOAD);

    assertEquals(Status.REJECTED, result.status());
    assertEquals("AA:BB:CC:DD:EE:FF", result.hardwareId());
    assertEquals("Erro interno ao registrar dispositivo", result.message());
  }
}
