package com.n0hana.echoes_server.auth.password;

import com.n0hana.echoes_server.mfa.TwoFactorService;
import com.n0hana.echoes_server.user.UserUtils;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

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
    public record RequestForgot(
            @NotBlank @Email @Size(max = UserUtils.EMAIL_MAX_LENGTH) String email) {
    }

    /**
     * DTO para redefinicação de senhas dos usuários
     * 
     * @param email       Email do usuário
     * @param token       token enviado pelo usuário
     * @param newPassword Nova senha do usuário
     */
    public record RequestReset(
            @NotBlank @Email @Size(max = UserUtils.EMAIL_MAX_LENGTH) String email,
            @NotBlank String token,
            @NotBlank @PasswordByteLength
            @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE) String newPassword) {
    }

    /**
     * DTO para validação de código de redefinição de senha
     * 
     * @param code Código de redefinição
     */
    public record ValidadeCodeRequest(
            @NotBlank @Email @Size(max = UserUtils.EMAIL_MAX_LENGTH) String email,
            @NotEmpty @Pattern(regexp = TwoFactorService.CODE_PATTERN, message = TwoFactorService.CODE_FORMAT_MESSAGE) String code) {
    }

    /**
     * DTO com token de redefinição de senha
     * 
     * @param token Token de redefinição
     */
    public record TokenResponse(
            String token) {
    }
}
