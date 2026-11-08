package com.n0hana.echoes_server.infra.mqtt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.n0hana.echoes_server.device.DeviceRegistrationPayload;
import com.n0hana.echoes_server.device.DeviceRegistrationResult;
import com.n0hana.echoes_server.device.DeviceRegistrationService;
import com.n0hana.echoes_server.device.exception.InvalidDeviceSignatureException;

import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class DeviceRegistrationMessageHandlerTests {

  @Mock
  private DeviceRegistrationService registrationService;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private DeviceRegistrationMessageHandler handler;

  @BeforeEach
  void setUp() {
    handler = new DeviceRegistrationMessageHandler(objectMapper, registrationService);
  }

  @Test
  @DisplayName("Lê public_key, signature e delega o registro")
  void shouldReadSnakeCasePayloadAndDelegate() throws Exception {
    String pem = "-----BEGIN PUBLIC KEY-----\nchave\n-----END PUBLIC KEY-----";
    String payload = objectMapper.writeValueAsString(Map.of(
        "mac", "AA:BB:CC:DD:EE:FF",
        "public_key", pem,
        "signature", "assinatura",
        "name", "Echoes Dev Kit"));

    when(registrationService.register(any(DeviceRegistrationPayload.class)))
        .thenReturn(DeviceRegistrationResult.registered(
            com.n0hana.echoes_server.device.DeviceModel.builder()
                .mac("AA:BB:CC:DD:EE:FF")
                .build()));

    DeviceRegistrationResult result = handler.handle(payload);

    assertEquals(DeviceRegistrationResult.Status.REGISTERED, result.status());

    ArgumentCaptor<DeviceRegistrationPayload> captor =
        ArgumentCaptor.forClass(DeviceRegistrationPayload.class);
    verify(registrationService).register(captor.capture());

    assertEquals("AA:BB:CC:DD:EE:FF", captor.getValue().mac());
    assertEquals(pem, captor.getValue().publicKey());
    assertEquals("assinatura", captor.getValue().signature());
    assertEquals("Echoes Dev Kit", captor.getValue().name());
  }

  @Test
  @DisplayName("Aceita também o campo publicKey em camelCase")
  void shouldAcceptCamelCasePublicKey() throws Exception {
    String payload = objectMapper.writeValueAsString(Map.of(
        "mac", "AA:BB:CC:DD:EE:FF",
        "publicKey", "pem",
        "signature", "assinatura"));

    when(registrationService.register(any(DeviceRegistrationPayload.class)))
        .thenReturn(DeviceRegistrationResult.rejected("AA:BB:CC:DD:EE:FF", "teste"));

    handler.handle(payload);

    ArgumentCaptor<DeviceRegistrationPayload> captor =
        ArgumentCaptor.forClass(DeviceRegistrationPayload.class);
    verify(registrationService).register(captor.capture());

    assertEquals("pem", captor.getValue().publicKey());
  }

  @Test
  @DisplayName("Rejeita JSON malformado sem lançar exceção")
  void shouldRejectMalformedJson() {
    DeviceRegistrationResult result = handler.handle("{mac:");

    assertEquals(DeviceRegistrationResult.Status.REJECTED, result.status());
    assertEquals("Payload de registro inválido", result.message());
  }

  @Test
  @DisplayName("Converte falha de assinatura em resultado rejeitado")
  void shouldConvertSignatureFailureToRejectedResult() throws Exception {
    String payload = objectMapper.writeValueAsString(Map.of(
        "mac", "AA:BB:CC:DD:EE:FF",
        "public_key", "pem",
        "signature", "assinatura"));

    when(registrationService.register(any(DeviceRegistrationPayload.class)))
        .thenThrow(new InvalidDeviceSignatureException());

    DeviceRegistrationResult result = handler.handle(payload);

    assertEquals(DeviceRegistrationResult.Status.REJECTED, result.status());
    assertEquals("Assinatura do dispositivo inválida", result.message());
  }

  @Test
  @DisplayName("Serializa o resultado em JSON")
  void shouldSerializeResult() throws Exception {
    String json = handler.toJson(
        DeviceRegistrationResult.rejected("AA:BB:CC:DD:EE:FF", "inválido"));

    assertTrue(json.contains("\"status\":\"REJECTED\""));
    assertTrue(json.contains("\"mac\":\"AA:BB:CC:DD:EE:FF\""));
  }
}
