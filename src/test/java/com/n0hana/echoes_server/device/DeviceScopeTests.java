package com.n0hana.echoes_server.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class DeviceScopeTests {

  private static final UUID OWN = UUID.randomUUID();
  private static final UUID OTHER = UUID.randomUUID();

  @Test
  @DisplayName("ADMIN sem filtro enxerga todas as instituições")
  void adminWithoutFilter() {
    assertNull(new DeviceScope.All().effectiveInstitution(null));
  }

  @Test
  @DisplayName("ADMIN pode filtrar por uma instituição")
  void adminWithFilter() {
    assertEquals(OTHER, new DeviceScope.All().effectiveInstitution(OTHER));
  }

  @Test
  @DisplayName("Escopo de instituição devolve sempre a própria")
  void institutionScopeUsesOwn() {
    DeviceScope scope = new DeviceScope.InstitutionScope(OWN);

    assertEquals(OWN, scope.effectiveInstitution(null));
    assertEquals(OWN, scope.effectiveInstitution(OWN));
  }

  @Test
  @DisplayName("Escopo de instituição recusa pedido de outra instituição")
  void institutionScopeRejectsOther() {
    DeviceScope scope = new DeviceScope.InstitutionScope(OWN);

    assertThrows(AccessDeniedException.class, () -> scope.effectiveInstitution(OTHER));
  }
}
