package com.n0hana.echoes_server.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class PasswordDTO {

    public record ResetRequest(
        @NotBlank
        @Email(message="E-mail inválido")
        String email,

        @NotBlank
        @Size(min=6, max=6, message="Código tem que ser 6 dígitos")
        String code,
        @NotBlank
        @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@#$%^&+=!]).{8,}$",
            message = "Senha fraca"
        )
        String newPassword,
        @NotBlank
        @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@#$%^&+=!]).{8,}$",
            message = "Senha fraca"
        )
        String confirmPassword
        ) {}

    public record ResetResponse(
        @NotBlank
        @Email(message="E-mail inválido")
        String message
        ) {}

    public record ForgotRequest(
        @NotBlank
        @Email(message="E-mail inválido")
        String email
        ) {}

    public record ForgotResponse(String message) {}

    public record ValidateRequest(
        @NotBlank
        String password
        ) {}

    public record ValidateResponse(String token) {}

    public record ChangeRequest(
        @NotBlank
        String token,
        @NotBlank
        @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@#$%^&+=!]).{8,}$",
            message = "Senha fraca"
        )
        String newPassword,
        @NotBlank
        @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@#$%^&+=!]).{8,}$",
            message = "Senha fraca"
        )
        String confirmPassword) {}

}
