package com.n0hana.echoes_server.device;

import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;

/** Alcance de consulta de um usuário sobre dispositivos e uso. */
public sealed interface DeviceScope permits DeviceScope.All, DeviceScope.InstitutionScope {

  /** Sem restrição (ADMIN). */
  record All() implements DeviceScope {
  }

  /** Restrito a uma instituição (gestor e professor). */
  record InstitutionScope(UUID institutionId) implements DeviceScope {
  }

  /**
   * Instituição efetiva de uma consulta. Em {@link All} devolve o filtro pedido
   * (nulo = todas); em {@link InstitutionScope} devolve sempre a própria e
   * recusa pedido de outra instituição.
   */
  default UUID effectiveInstitution(UUID requested) {
    if (this instanceof InstitutionScope scope) {
      if (requested != null && !requested.equals(scope.institutionId()))
        throw new AccessDeniedException("Sem acesso a outra instituição");
      return scope.institutionId();
    }
    return requested;
  }
}
