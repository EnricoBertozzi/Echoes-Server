package com.n0hana.echoes_server.device;

import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import org.springframework.stereotype.Service;

import com.n0hana.echoes_server.device.exception.InvalidDevicePublicKeyException;

/**
 * Interpreta a chave pública EC armazenada no cadastro do dispositivo.
 *
 * <p>
 * A chave é persistida como texto PEM no banco de dados. Este serviço apenas a
 * converte para {@link ECPublicKey}, usada na validação das assinaturas ES256.
 * </p>
 */
@Service
public class DevicePublicKeyService {

  private static final ECParameterSpec P256 = loadP256Parameters();

  /**
   * Converte o texto PEM em uma chave pública EC.
   *
   * @param pem conteúdo PEM da chave pública
   * @return {@link ECPublicKey} pronta para validar assinaturas ES256
   */
  public ECPublicKey parse(String pem) {
    if (pem == null || pem.isBlank())
      throw new InvalidDevicePublicKeyException();

    String publicKeyContent = pem
        .replace("-----BEGIN PUBLIC KEY-----", "")
        .replace("-----END PUBLIC KEY-----", "")
        .replaceAll("\\s", "");

    try {
      byte[] decoded = Base64.getDecoder().decode(publicKeyContent);
      PublicKey publicKey = KeyFactory.getInstance("EC")
          .generatePublic(new X509EncodedKeySpec(decoded));

      if (!(publicKey instanceof ECPublicKey ecPublicKey))
        throw new InvalidDevicePublicKeyException();

      if (!isP256(ecPublicKey))
        throw new InvalidDevicePublicKeyException();

      return ecPublicKey;
    } catch (InvalidDevicePublicKeyException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new InvalidDevicePublicKeyException();
    }
  }

  private boolean isP256(ECPublicKey publicKey) {
    ECParameterSpec parameters = publicKey.getParams();

    return parameters.getCurve().equals(P256.getCurve())
        && parameters.getGenerator().equals(P256.getGenerator())
        && parameters.getOrder().equals(P256.getOrder())
        && parameters.getCofactor() == P256.getCofactor();
  }

  private static ECParameterSpec loadP256Parameters() {
    try {
      AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
      parameters.init(new ECGenParameterSpec("secp256r1"));
      return parameters.getParameterSpec(ECParameterSpec.class);
    } catch (Exception exception) {
      throw new IllegalStateException("Curva P-256 indisponível", exception);
    }
  }
}
