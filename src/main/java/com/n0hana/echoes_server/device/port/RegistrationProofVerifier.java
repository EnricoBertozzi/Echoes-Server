package com.n0hana.echoes_server.device.port;

import java.security.interfaces.ECPublicKey;

/**
 * Porta: confere a prova de posse da chave enviada no cadastro.
 */
public interface RegistrationProofVerifier {

  /**
   * @throws com.n0hana.echoes_server.device.exception.InvalidDeviceSignatureException
   *         quando a prova não confere
   */
  void verifyRegistrationSignature(ECPublicKey publicKey, String hardwareId, String pem, String signature);
}
