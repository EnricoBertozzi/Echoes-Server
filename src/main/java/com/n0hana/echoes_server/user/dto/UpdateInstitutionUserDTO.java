package com.n0hana.echoes_server.user.dto;

import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UpdateInstitutionUserDTO(
    @Size(min = 1, message = "O nome não pode ficar em branco")
    String name,

    @Size(min = 1, message = "O e-mail não pode ficar em branco")
    @Email(message = "E-mail inválido")
    String email,

    UUID institutionId
) {}
