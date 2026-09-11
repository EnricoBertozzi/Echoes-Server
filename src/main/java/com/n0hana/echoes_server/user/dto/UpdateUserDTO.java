package com.n0hana.echoes_server.user.dto;

import jakarta.validation.constraints.Email;

public record UpdateUserDTO(
    String name,

    @Email(message = "E-mail inválido")
    String email
) {}
