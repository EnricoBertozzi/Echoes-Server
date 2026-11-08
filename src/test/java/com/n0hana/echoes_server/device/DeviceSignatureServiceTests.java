package com.n0hana.echoes_server.device;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.HexFormat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.n0hana.echoes_server.device.exception.InvalidDeviceSignatureException;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;

class DeviceSignatureServiceTests {

  private static final String MAC = "AA:BB:CC:DD:EE:FF";

  private final DeviceSignatureService signatureService = new DeviceSignatureService();

  private KeyPair keyPair;
  private ECPublicKey publicKey;
  private ECPrivateKey privateKey;
  private String pem;

  @BeforeEach
  void setUp() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
    generator.initialize(new ECGenParameterSpec("secp256r1"));
    keyPair = generator.generateKeyPair();

    publicKey = (ECPublicKey) keyPair.getPublic();
    privateKey = (ECPrivateKey) keyPair.getPrivate();
    pem = toPem(keyPair);
  }

  @Test
  @DisplayName("Monta a mensagem canônica com contexto, MAC e chave pública")
  void shouldBuildCanonicalRegistrationMessage() {
    String message = new String(
        signatureService.registrationMessage("  " + MAC + "  ", "  " + pem + "  "),
        StandardCharsets.UTF_8);

    assertEquals(
        DeviceSignatureService.REGISTRATION_CONTEXT + "\n" + MAC + "\n" + pem.trim(),
        message);
  }

  @Test
  @DisplayName("Aceita assinatura ASN.1/DER em Base64")
  void shouldAcceptDerSignatureInBase64() throws Exception {
    byte[] message = signatureService.registrationMessage(MAC, pem);
    String signature = Base64.getEncoder().encodeToString(signDer(message));

    assertDoesNotThrow(() -> signatureService.verify(publicKey, message, signature));
  }

  @Test
  @DisplayName("Aceita assinatura JOSE em Base64 URL")
  void shouldAcceptJoseSignatureInBase64Url() throws Exception {
    byte[] message = signatureService.registrationMessage(MAC, pem);
    String signature = signJose(message);

    assertDoesNotThrow(() -> signatureService.verify(publicKey, message, signature));
  }

  @Test
  @DisplayName("Aceita assinatura em hexadecimal")
  void shouldAcceptHexSignature() throws Exception {
    byte[] message = signatureService.registrationMessage(MAC, pem);
    String signature = HexFormat.of().formatHex(signDer(message));

    assertDoesNotThrow(() -> signatureService.verify(publicKey, message, signature));
  }

  @Test
  @DisplayName("Rejeita assinatura sobre outra mensagem")
  void shouldRejectSignatureForAnotherMessage() throws Exception {
    byte[] message = signatureService.registrationMessage(MAC, pem);
    byte[] anotherMessage = signatureService.registrationMessage("11:22:33:44:55:66", pem);
    String signature = Base64.getEncoder().encodeToString(signDer(anotherMessage));

    assertThrows(
        InvalidDeviceSignatureException.class,
        () -> signatureService.verify(publicKey, message, signature));
  }

  @Test
  @DisplayName("Rejeita assinatura feita com outra chave privada")
  void shouldRejectSignatureFromAnotherKey() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
    generator.initialize(new ECGenParameterSpec("secp256r1"));
    KeyPair anotherKeyPair = generator.generateKeyPair();

    byte[] message = signatureService.registrationMessage(MAC, pem);
    Signature signer = Signature.getInstance("SHA256withECDSA");
    signer.initSign(anotherKeyPair.getPrivate());
    signer.update(message);
    String signature = Base64.getEncoder().encodeToString(signer.sign());

    assertThrows(
        InvalidDeviceSignatureException.class,
        () -> signatureService.verify(publicKey, message, signature));
  }

  @Test
  @DisplayName("Rejeita assinatura vazia ou malformada")
  void shouldRejectBlankOrMalformedSignature() {
    byte[] message = signatureService.registrationMessage(MAC, pem);

    assertThrows(
        InvalidDeviceSignatureException.class,
        () -> signatureService.verify(publicKey, message, null));
    assertThrows(
        InvalidDeviceSignatureException.class,
        () -> signatureService.verify(publicKey, message, "   "));
    assertThrows(
        InvalidDeviceSignatureException.class,
        () -> signatureService.verify(publicKey, message, "não-é-assinatura"));
  }

  private byte[] signDer(byte[] message) throws Exception {
    Signature signer = Signature.getInstance("SHA256withECDSA");
    signer.initSign(privateKey);
    signer.update(message);
    return signer.sign();
  }

  private String signJose(byte[] message) throws Exception {
    return new ECDSASigner(privateKey)
        .sign(new JWSHeader(JWSAlgorithm.ES256), message)
        .toString();
  }

  private String toPem(KeyPair pair) {
    String base64 = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
    return "-----BEGIN PUBLIC KEY-----\n" + base64 + "\n-----END PUBLIC KEY-----";
  }
}
