package com.n0hana.echoes_server.device.port;

import com.n0hana.echoes_server.device.DeviceScope;
import com.n0hana.echoes_server.user.model.User;

/**
 * Porta: diz o que o usuário pode enxergar. ADMIN vê tudo; gestor e professor
 * só a própria instituição.
 */
public interface DeviceScopeResolver {

  /**
   * @throws org.springframework.security.access.AccessDeniedException quando o
   *         papel não tem escopo (estudante) ou o usuário não tem instituição
   */
  DeviceScope resolve(User user);
}
