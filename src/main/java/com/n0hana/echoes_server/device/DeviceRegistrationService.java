package com.n0hana.echoes_server.device;

import java.security.interfaces.ECPublicKey;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.n0hana.echoes_server.device.port.DeviceRegistrationUseCase;
import com.n0hana.echoes_server.device.port.RegistrationProofVerifier;

import com.n0hana.echoes_server.device.exception.InvalidDevicePublicKeyException;
import com.n0hana.echoes_server.device.exception.InvalidDeviceRegistrationException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DeviceRegistrationService implements DeviceRegistrationUseCase {

  private static final int MAX_HARDWARE_ID_LENGTH = 255;
  private static final int MAX_PUBLIC_KEY_LENGTH = 8192;
  private static final int MAX_NAME_LENGTH = 255;

  private final DeviceRepository deviceRepository;
  private final DevicePublicKeyService publicKeyService;
  private final RegistrationProofVerifier signatureService;

  @Override
  public DeviceRegistrationResult register(DeviceRegistrationPayload payload) {
    String hardwareId = normalizeHardwareId(payload);
    String publicKey = normalizePublicKey(payload);
    String signature = payload != null ? payload.signature() : null;

    ECPublicKey submittedKey = publicKeyService.parse(publicKey);
    signatureService.verifyRegistrationSignature(submittedKey, hardwareId, publicKey, signature);

    Optional<DeviceModel> existing = deviceRepository.findByHardwareId(hardwareId);
    if (existing.isPresent())
      return resultForExisting(existing.get(), submittedKey);

    DeviceModel device = DeviceModel.builder()
        .name(normalizeName(payload.name(), hardwareId))
        .hardwareId(hardwareId)
        .publicKey(publicKey)
        .status(DeviceStatus.PENDING)
        .build();

    try {
      DeviceModel saved = deviceRepository.saveAndFlush(device);
      return DeviceRegistrationResult.registered(saved);
    } catch (DataIntegrityViolationException exception) {
      // Corrida entre dois registros do mesmo hardware_id: relê e decide pelo estado final.
      return deviceRepository.findByHardwareId(hardwareId)
          .map(concurrent -> resultForExisting(concurrent, submittedKey))
          .orElseGet(() -> DeviceRegistrationResult.rejected(
              hardwareId,
              "Não foi possível registrar o dispositivo"));
    }
  }

  private DeviceRegistrationResult resultForExisting(DeviceModel device, ECPublicKey submittedKey) {
    try {
      ECPublicKey storedKey = publicKeyService.parse(device.getPublicKey());
      if (storedKey.equals(submittedKey))
        return DeviceRegistrationResult.alreadyRegistered(device);
    } catch (InvalidDevicePublicKeyException exception) {
    }

    return DeviceRegistrationResult.rejected(
        device.getHardwareId(),
        "Dispositivo já cadastrado com outra chave pública");
  }

  private String normalizeHardwareId(DeviceRegistrationPayload payload) {
    if (payload == null || payload.hardwareId() == null || payload.hardwareId().isBlank())
      throw new InvalidDeviceRegistrationException("hardware_id é obrigatório");

    String hardwareId = payload.hardwareId().trim();
    if (hardwareId.length() > MAX_HARDWARE_ID_LENGTH)
      throw new InvalidDeviceRegistrationException("hardware_id inválido");

    return hardwareId;
  }

  private String normalizePublicKey(DeviceRegistrationPayload payload) {
    if (payload == null || payload.publicKey() == null || payload.publicKey().isBlank())
      throw new InvalidDeviceRegistrationException("Chave pública é obrigatória");

    String publicKey = payload.publicKey().trim();
    if (publicKey.length() > MAX_PUBLIC_KEY_LENGTH)
      throw new InvalidDeviceRegistrationException("Chave pública inválida");

    return publicKey;
  }

  private String normalizeName(String name, String hardwareId) {
    if (name == null || name.isBlank())
      return hardwareId;

    String normalized = name.trim();
    return normalized.length() <= MAX_NAME_LENGTH ? normalized : hardwareId;
  }
}
