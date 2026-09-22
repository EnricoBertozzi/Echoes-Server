package com.n0hana.echoes_server.auth.password;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Conjunto de DTOs para consumo de endpoints relacionados a redefinação de
 * senhas
 * 
 * @author Enrico Bertozzi
 * @since 0.1.1
 * @see {@link PasswordController}
 */
public class PasswordDTO {

    /**
     * DTO para requisição de alteração de senha
     * 
     * @param email Email do usuário
     */
    public record RequestReset(
            @NotBlank @Email String email) {
    }

    /**
     * DTO para redefinicação de senhas dos usuários
     * 
     * @param email       Email do usuário
     * @param code        Código multifator enviado pelo usuário
     * @param newPassword Nova senha do usuário
     */
    public record RequestPassword(
            @NotBlank @Email String email,
            @NotBlank String code,
            @NotBlank String newPassword) {
    }
}
