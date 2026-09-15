package com.n0hana.echoes_server.user.dto;

import java.util.UUID;

import com.n0hana.echoes_server.user.UserRole;

public record UserDTO(
    UUID id,
    String name,
    String email,
    UUID institutionId,
    UserRole role
) {}
