package com.n0hana.echoes_server.device.port;

import com.n0hana.echoes_server.device.DeviceModel;
import com.n0hana.echoes_server.device.exception.DeviceAuthenticationException;

/**
 * Porta: como o dispositivo prova quem é a cada chamada.
 *
 * <p>
 * Hoje é um JWT ES256 assinado pelo próprio ESP. Se o grupo adotar o token
 * derivado da secret, basta outra implementação desta interface. A verificação
 * só identifica o dispositivo; se ele pode operar é decidido por
 * {@link DeviceAccessPolicy}.
 * </p>
 */
public interface DeviceCredentialVerifier {

  /** Indica se esta implementação reconhece o formato da credencial. */
  boolean supports(String credential);

  /**
   * Valida a credencial e devolve o dispositivo.
   *
   * @throws DeviceAuthenticationException quando a credencial é inválida
   */
  DeviceModel verify(String credential);
}
