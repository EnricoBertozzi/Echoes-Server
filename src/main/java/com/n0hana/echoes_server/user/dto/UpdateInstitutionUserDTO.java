package com.n0hana.echoes_server.user.dto;

import java.util.UUID;

import jakarta.validation.constraints.Email;

public record UpdateInstitutionUserDTO(
    String name,

    @Email(message = "E-mail inválido")
    String email,

    UUID institutionId
) {}
