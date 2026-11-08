package com.n0hana.echoes_server.device;

import java.security.interfaces.ECPublicKey;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.n0hana.echoes_server.device.exception.InvalidDevicePublicKeyException;
import com.n0hana.echoes_server.device.exception.InvalidDeviceRegistrationException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DeviceRegistrationService {

  private static final int MAX_MAC_LENGTH = 255;
  private static final int MAX_PUBLIC_KEY_LENGTH = 8192;
  private static final int MAX_NAME_LENGTH = 255;

  private final DeviceRepository deviceRepository;
  private final DevicePublicKeyService publicKeyService;
  private final DeviceSignatureService signatureService;

  public DeviceRegistrationResult register(DeviceRegistrationPayload payload) {
    String mac = normalizeMac(payload);
    String publicKey = normalizePublicKey(payload);
    String signature = payload != null ? payload.signature() : null;

    ECPublicKey submittedKey = publicKeyService.parse(publicKey);
    signatureService.verifyRegistrationSignature(submittedKey, mac, publicKey, signature);

    Optional<DeviceModel> existing = deviceRepository.findByMac(mac);
    if (existing.isPresent())
      return resultForExisting(existing.get(), submittedKey);

    DeviceModel device = DeviceModel.builder()
        .name(normalizeName(payload.name(), mac))
        .mac(mac)
        .publicKey(publicKey)
        .build();

    try {
      DeviceModel saved = deviceRepository.saveAndFlush(device);
      return DeviceRegistrationResult.registered(saved);
    } catch (DataIntegrityViolationException exception) {
      // Corrida entre dois registros do mesmo MAC: relê e decide pelo estado final.
      return deviceRepository.findByMac(mac)
          .map(concurrent -> resultForExisting(concurrent, submittedKey))
          .orElseGet(() -> DeviceRegistrationResult.rejected(
              mac,
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
        device.getMac(),
        "MAC já cadastrado com outra chave pública");
  }

  private String normalizeMac(DeviceRegistrationPayload payload) {
    if (payload == null || payload.mac() == null || payload.mac().isBlank())
      throw new InvalidDeviceRegistrationException("MAC é obrigatório");

    String mac = payload.mac().trim();
    if (mac.length() > MAX_MAC_LENGTH)
      throw new InvalidDeviceRegistrationException("MAC inválido");

    return mac;
  }

  private String normalizePublicKey(DeviceRegistrationPayload payload) {
    if (payload == null || payload.publicKey() == null || payload.publicKey().isBlank())
      throw new InvalidDeviceRegistrationException("Chave pública é obrigatória");

    String publicKey = payload.publicKey().trim();
    if (publicKey.length() > MAX_PUBLIC_KEY_LENGTH)
      throw new InvalidDeviceRegistrationException("Chave pública inválida");

    return publicKey;
  }

  private String normalizeName(String name, String mac) {
    if (name == null || name.isBlank())
      return mac;

    String normalized = name.trim();
    return normalized.length() <= MAX_NAME_LENGTH ? normalized : mac;
  }
}
