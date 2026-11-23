package com.n0hana.echoes_server.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateUserDTO(
    @NotBlank(message = "O nome é obrigatório")
    String name,

    @NotBlank(message = "O sobrenome é obrigatório")
    String surname,

    @NotBlank(message = "O e-mail é obrigatório")
    @Email(message = "E-mail inválido")
    String email
) {}
