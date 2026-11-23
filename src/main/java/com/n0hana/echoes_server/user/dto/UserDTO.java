package com.n0hana.echoes_server.user.dto;

import java.util.UUID;

import com.n0hana.echoes_server.user.model.UserRole;

public record UserDTO(
    UUID id,
    String name,
    String surname,
    String email,
    UUID institutionId,
    UserRole role,
    boolean mustChangePassword
) {

  /** Forma sem a flag de troca obrigatória (listagens e usos que não a expõem). */
  public UserDTO(UUID id, String name, String surname, String email, UUID institutionId, UserRole role) {
    this(id, name, surname, email, institutionId, role, false);
  }
}
