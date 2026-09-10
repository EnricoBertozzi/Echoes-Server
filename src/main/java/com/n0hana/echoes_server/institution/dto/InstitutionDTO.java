package com.n0hana.echoes_server.institution.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.br.CNPJ;

import java.util.UUID;

public record InstitutionDTO(
    UUID id,

    @NotBlank(message = "O nome é obrigatório")
    String name,

    @NotBlank(message = "A sigla é obrigatória")
    String acronym,

    @NotBlank(message = "O CNPJ é obrigatório")
    @CNPJ(message = "CNPJ inválido")
    String cnpj,

    @NotBlank(message = "O e-mail é obrigatório")
    @Email(message = "E-mail inválido")
    String email,

    String phone,
    Boolean active
) {}
