package com.n0hana.echoes_server.device;

import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.stereotype.Service;

import com.n0hana.echoes_server.device.exception.InvalidDeviceSignatureException;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.util.Base64URL;

@Service
public class DeviceSignatureService {

  public static final String REGISTRATION_CONTEXT = "echoes-device-registration-v1";

  private static final int JOSE_SIGNATURE_LENGTH = 64;
  private static final int DER_SEQUENCE_TAG = 0x30;
  private static final int MIN_HEX_SIGNATURE_LENGTH = JOSE_SIGNATURE_LENGTH * 2;

  public byte[] registrationMessage(String mac, String publicKey) {
    String message = REGISTRATION_CONTEXT
        + "\n" + normalize(mac)
        + "\n" + normalize(publicKey);

    return message.getBytes(StandardCharsets.UTF_8);
  }

  public void verifyRegistrationSignature(
      ECPublicKey publicKey,
      String mac,
      String pem,
      String signature) {
    verify(publicKey, registrationMessage(mac, pem), signature);
  }

  public void verify(ECPublicKey publicKey, byte[] message, String signature) {
    if (publicKey == null)
      throw new InvalidDeviceSignatureException();

    byte[] decoded = decodeSignature(signature);

    try {
      boolean valid;

      if (decoded.length == JOSE_SIGNATURE_LENGTH) {
        valid = new ECDSAVerifier(publicKey).verify(
            new JWSHeader(JWSAlgorithm.ES256),
            message,
            Base64URL.encode(decoded));
      } else if (decoded.length > 0 && (decoded[0] & 0xff) == DER_SEQUENCE_TAG) {
        Signature verifier = Signature.getInstance("SHA256withECDSA");
        verifier.initVerify(publicKey);
        verifier.update(message);
        valid = verifier.verify(decoded);
      } else {
        valid = false;
      }

      if (!valid)
        throw new InvalidDeviceSignatureException();
    } catch (InvalidDeviceSignatureException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new InvalidDeviceSignatureException();
    }
  }

  private byte[] decodeSignature(String signature) {
    if (signature == null || signature.isBlank())
      throw new InvalidDeviceSignatureException();

    String value = signature.trim();

    if (value.startsWith("0x") || value.startsWith("0X"))
      return decodeHex(value.substring(2));

    byte[] base64 = tryBase64(value);
    if (isPlausibleSignature(base64))
      return base64;

    if (isHex(value)) {
      byte[] hex = decodeHex(value);
      if (isPlausibleSignature(hex))
        return hex;
    }

    throw new InvalidDeviceSignatureException();
  }

  private byte[] tryBase64(String value) {
    String padded = padBase64(value);

    try {
      return Base64.getDecoder().decode(padded);
    } catch (IllegalArgumentException ignored) {
      // tenta o alfabeto URL-safe
    }

    try {
      return Base64.getUrlDecoder().decode(padded);
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }

  private String padBase64(String value) {
    int remainder = value.length() % 4;
    if (remainder == 2)
      return value + "==";
    if (remainder == 3)
      return value + "=";
    return value;
  }

  private boolean isPlausibleSignature(byte[] signature) {
    if (signature == null || signature.length == 0)
      return false;

    return signature.length == JOSE_SIGNATURE_LENGTH
        || (signature[0] & 0xff) == DER_SEQUENCE_TAG;
  }

  private boolean isHex(String value) {
    return value.length() >= MIN_HEX_SIGNATURE_LENGTH
        && value.length() % 2 == 0
        && value.matches("[0-9a-fA-F]+");
  }

  private byte[] decodeHex(String value) {
    try {
      return HexFormat.of().parseHex(value);
    } catch (IllegalArgumentException exception) {
      throw new InvalidDeviceSignatureException();
    }
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim();
  }
}
