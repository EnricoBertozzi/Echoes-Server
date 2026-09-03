package com.n0hana.echoes_server.dto;

import com.n0hana.echoes_server.service.validation.PasswordValueMatch;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@PasswordValueMatch.List({
    @PasswordValueMatch(
        field = "password",
        fieldMatch = "confirmPassword",
        message = "Senhas não correspondem"
    )
})
public record RegisterRequestDTO(
    @NotBlank
    String name,

    @NotBlank
    @Email(message="E-mail inválido")
    String email,

    @NotBlank
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@#$%^&+=!]).{8,}$",
        message = "Senha fraca"
    )
    String password,

    @NotBlank
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@#$%^&+=!]).{8,}$",
        message = "Senha fraca"
    )
    String confirmPassword
) {
    
}
