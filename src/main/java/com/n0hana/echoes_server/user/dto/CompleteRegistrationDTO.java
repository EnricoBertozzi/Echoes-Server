package com.n0hana.echoes_server.user.dto;

import java.util.List;

import com.n0hana.echoes_server.term.model.DocumentType;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CompleteRegistrationDTO(
        @NotBlank(message = "O e-mail é obrigatório") @Email(message = "E-mail inválido") String email,

        @NotBlank(message = "A senha é obrigatória") @Pattern(regexp = "^$|^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@#$%^&+=!]).{8,}$", message = "Senha fraca") String password,

        @NotBlank(message = "O código é obrigatório") String code,

        @NotNull(message = "A lista de termos aceitos é obrigatória") List<DocumentType> acceptedTerms) {
}
