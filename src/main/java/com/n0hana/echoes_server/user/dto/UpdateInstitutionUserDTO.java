package com.n0hana.echoes_server.user.dto;

import java.util.UUID;

import jakarta.validation.constraints.Size;

public record UpdateInstitutionUserDTO(
    @Size(min = 1, message = "O nome não pode ficar em branco")
    String name,

    UUID institutionId
) {}
