package com.n0hana.echoes_server.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.n0hana.echoes_server.device.exception.InvalidDevicePublicKeyException;
import com.n0hana.echoes_server.device.exception.InvalidDeviceRegistrationException;
import com.n0hana.echoes_server.device.exception.InvalidDeviceSignatureException;

@ExtendWith(MockitoExtension.class)
class DeviceRegistrationServiceTests {

  private static final String MAC = "AA:BB:CC:DD:EE:FF";
  private static final UUID DEVICE_ID = UUID.randomUUID();

  @Mock
  private DeviceRepository deviceRepository;

  private DeviceSignatureService signatureService;
  private DeviceRegistrationService registrationService;

  private KeyPair keyPair;
  private String pem;

  @BeforeEach
  void setUp() throws Exception {
    signatureService = new DeviceSignatureService();
    registrationService = new DeviceRegistrationService(
        deviceRepository,
        new DevicePublicKeyService(),
        signatureService);

    KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
    generator.initialize(new ECGenParameterSpec("secp256r1"));
    keyPair = generator.generateKeyPair();
    pem = toPem(keyPair);
  }

  @Test
  @DisplayName("Registra um dispositivo novo como PENDING, sem instituição, com a chave e o nome padrão")
  void shouldRegisterNewDevice() throws Exception {
    when(deviceRepository.findByHardwareId(MAC)).thenReturn(Optional.empty());
    when(deviceRepository.saveAndFlush(any(DeviceModel.class))).thenAnswer(invocation -> {
      DeviceModel device = invocation.getArgument(0);
      device.setId(DEVICE_ID);
      return device;
    });

    DeviceRegistrationResult result = registrationService.register(payload(MAC, pem, null));

    assertEquals(DeviceRegistrationResult.Status.REGISTERED, result.status());
    assertEquals(MAC, result.hardwareId());
    assertEquals(DEVICE_ID, result.deviceId());
    assertEquals(DeviceStatus.PENDING, result.deviceStatus());

    ArgumentCaptor<DeviceModel> captor = ArgumentCaptor.forClass(DeviceModel.class);
    verify(deviceRepository).saveAndFlush(captor.capture());

    DeviceModel saved = captor.getValue();
    assertEquals(DeviceStatus.PENDING, saved.getStatus());
    assertNull(saved.getInstitution());
    assertEquals(MAC, saved.getHardwareId());
    assertEquals(pem, saved.getPublicKey());
    assertEquals(MAC, saved.getName());
  }

  @Test
  @DisplayName("Usa o nome enviado no payload quando presente")
  void shouldUseNameFromPayload() throws Exception {
    when(deviceRepository.findByHardwareId(MAC)).thenReturn(Optional.empty());
    when(deviceRepository.saveAndFlush(any(DeviceModel.class))).thenAnswer(invocation -> {
      DeviceModel device = invocation.getArgument(0);
      device.setId(DEVICE_ID);
      return device;
    });

    registrationService.register(payload(MAC, pem, "Echoes Dev Kit"));

    ArgumentCaptor<DeviceModel> captor = ArgumentCaptor.forClass(DeviceModel.class);
    verify(deviceRepository).saveAndFlush(captor.capture());

    assertEquals("Echoes Dev Kit", captor.getValue().getName());
  }

  @Test
  @DisplayName("É idempotente quando o MAC já existe com a mesma chave")
  void shouldReturnAlreadyRegisteredForSameKey() throws Exception {
    DeviceModel existing = DeviceModel.builder()
        .id(DEVICE_ID)
        .name("Dispositivo")
        .hardwareId(MAC)
        .publicKey(pem)
        .build();

    when(deviceRepository.findByHardwareId(MAC)).thenReturn(Optional.of(existing));

    DeviceRegistrationResult result = registrationService.register(payload(MAC, pem, null));

    assertEquals(DeviceRegistrationResult.Status.ALREADY_REGISTERED, result.status());
    assertEquals(DEVICE_ID, result.deviceId());
    verify(deviceRepository, never()).saveAndFlush(any(DeviceModel.class));
  }

  @Test
  @DisplayName("Rejeita MAC já cadastrado com outra chave pública")
  void shouldRejectMacRegisteredWithAnotherKey() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
    generator.initialize(new ECGenParameterSpec("secp256r1"));
    KeyPair anotherKeyPair = generator.generateKeyPair();

    DeviceModel existing = DeviceModel.builder()
        .id(DEVICE_ID)
        .hardwareId(MAC)
        .publicKey(toPem(anotherKeyPair))
        .build();

    when(deviceRepository.findByHardwareId(MAC)).thenReturn(Optional.of(existing));

    DeviceRegistrationResult result = registrationService.register(payload(MAC, pem, null));

    assertEquals(DeviceRegistrationResult.Status.REJECTED, result.status());
    assertEquals("MAC já cadastrado com outra chave pública", result.message());
    verify(deviceRepository, never()).saveAndFlush(any(DeviceModel.class));
  }

  @Test
  @DisplayName("Rejeita assinatura inválida")
  void shouldRejectInvalidSignature() throws Exception {
    DeviceRegistrationPayload payload = new DeviceRegistrationPayload(
        MAC,
        pem,
        sign("outra mensagem".getBytes()),
        null);

    assertThrows(
        InvalidDeviceSignatureException.class,
        () -> registrationService.register(payload));
  }

  @Test
  @DisplayName("Rejeita payload sem MAC")
  void shouldRejectPayloadWithoutMac() throws Exception {
    assertThrows(
        InvalidDeviceRegistrationException.class,
        () -> registrationService.register(payload("  ", pem, null)));
  }

  @Test
  @DisplayName("Rejeita chave pública inválida")
  void shouldRejectInvalidPublicKey() {
    DeviceRegistrationPayload payload = new DeviceRegistrationPayload(
        MAC,
        "não-é-uma-chave",
        "assinatura",
        null);

    assertThrows(
        InvalidDevicePublicKeyException.class,
        () -> registrationService.register(payload));
  }

  @Test
  @DisplayName("Trata corrida de cadastro relendo o dispositivo existente")
  void shouldHandleRegistrationRace() throws Exception {
    DeviceModel existing = DeviceModel.builder()
        .id(DEVICE_ID)
        .hardwareId(MAC)
        .publicKey(pem)
        .build();

    when(deviceRepository.findByHardwareId(MAC))
        .thenReturn(Optional.empty(), Optional.of(existing));
    when(deviceRepository.saveAndFlush(any(DeviceModel.class)))
        .thenThrow(new DataIntegrityViolationException("duplicate hardwareId"));

    DeviceRegistrationResult result = registrationService.register(payload(MAC, pem, null));

    assertEquals(DeviceRegistrationResult.Status.ALREADY_REGISTERED, result.status());
    assertEquals(DEVICE_ID, result.deviceId());
  }

  private DeviceRegistrationPayload payload(String hardwareId, String publicKey, String name) throws Exception {
    String signature = sign(signatureService.registrationMessage(hardwareId, publicKey));
    return new DeviceRegistrationPayload(hardwareId, publicKey, signature, name);
  }

  private String sign(byte[] message) throws Exception {
    Signature signer = Signature.getInstance("SHA256withECDSA");
    signer.initSign(keyPair.getPrivate());
    signer.update(message);
    return Base64.getEncoder().encodeToString(signer.sign());
  }

  private String toPem(KeyPair pair) {
    String base64 = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
    return "-----BEGIN PUBLIC KEY-----\n" + base64 + "\n-----END PUBLIC KEY-----";
  }
}
