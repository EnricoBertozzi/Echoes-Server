package com.n0hana.echoes_server.device;

import java.security.interfaces.ECPublicKey;
import java.util.Date;

import org.springframework.stereotype.Service;

import com.n0hana.echoes_server.device.exception.DeviceAuthenticationException;
import com.n0hana.echoes_server.device.port.DeviceCredentialVerifier;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import lombok.RequiredArgsConstructor;

/**
 * Valida os tokens autoassinados pelos dispositivos.
 *
 * <p>
 * O dispositivo gera um JWT ES256 com as claims {@code iss=echoes-device},
 * {@code hardware_id} e {@code exp}. O servidor localiza o dispositivo pelo hardware_id,
 * recupera a chave pública cadastrada e valida a assinatura. Como esses tokens
 * têm curta duração, não há necessidade de revogação/lista negra.
 * </p>
 */
@Service
@RequiredArgsConstructor
public class DeviceJwtService implements DeviceCredentialVerifier {

  public static final String DEVICE_ISSUER = "echoes-device";
  public static final String HARDWARE_ID_CLAIM = "hardware_id";
  /** vida máxima aceita para o token do dispositivo (S12) */
  static final long MAX_TOKEN_LIFETIME_MS = 5L * 60L * 1000L;
  /** tolerância para relógio do dispositivo adiantado */
  static final long CLOCK_SKEW_MS = 60L * 1000L;

  private final DeviceRepository deviceRepository;
  private final DevicePublicKeyService publicKeyService;

  @Override
  public boolean supports(String credential) {
    try {
      return DEVICE_ISSUER.equals(SignedJWT.parse(credential).getJWTClaimsSet().getIssuer());
    } catch (Exception exception) {
      return false;
    }
  }

  @Override
  public DeviceModel verify(String credential) {
    try {
      return validateToken(credential);
    } catch (Exception exception) {
      throw new DeviceAuthenticationException(exception);
    }
  }

  public DeviceModel validateToken(String token) throws Exception {
    SignedJWT signedJWT = SignedJWT.parse(token);
    JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

    if (!DEVICE_ISSUER.equals(claims.getIssuer()))
      throw new SecurityException("Invalid issuer");

    if (!JWSAlgorithm.ES256.equals(signedJWT.getHeader().getAlgorithm()))
      throw new SecurityException("Invalid algorithm");

    // a claim legada "mac" não é mais aceita (S12)
    String hardwareId = claims.getStringClaim(HARDWARE_ID_CLAIM);
    if (hardwareId == null || hardwareId.isBlank())
      throw new SecurityException("Missing hardware_id claim");

    DeviceModel device = deviceRepository
        .findByHardwareId(hardwareId.trim())
        .orElseThrow(() -> new SecurityException("Device not found"));

    ECPublicKey publicKey = publicKeyService.parse(device.getPublicKey());

    if (!signedJWT.verify(new ECDSAVerifier(publicKey)))
      throw new SecurityException("Invalid JWT signature");

    Date now = new Date();

    Date issuedAt = claims.getIssueTime();
    if (issuedAt == null)
      throw new SecurityException("Missing iat claim");
    if (issuedAt.getTime() > now.getTime() + CLOCK_SKEW_MS)
      throw new SecurityException("iat in the future");

    Date expiration = claims.getExpirationTime();
    if (expiration == null || expiration.before(now))
      throw new SecurityException("JWT expired");
    if (expiration.getTime() - now.getTime() > MAX_TOKEN_LIFETIME_MS)
      throw new SecurityException("JWT lifetime too long");

    return device;
  }
}
