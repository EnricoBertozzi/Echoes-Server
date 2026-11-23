package com.n0hana.echoes_server.auth;

import com.n0hana.echoes_server.auth.password.PasswordByteLength;
import com.n0hana.echoes_server.auth.password.PasswordPolicy;
import com.n0hana.echoes_server.mfa.TwoFactorService;
import com.n0hana.echoes_server.user.UserUtils;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Conjunto de DTOs para consumo dos endpoints da API relacionados a
 * autenticação
 * 
 * @author Enrico Bertozzi
 * @since 0.1.1
 * @see {@link AuthController}
 */
public class AuthDTO {

    /**
     * DTO para login de usuários.
     * 
     * @param email    Email do usuário
     * @param password Senha do usuário
     */
    public record LoginRequest(
            @NotBlank @Email @Size(max = UserUtils.EMAIL_MAX_LENGTH) String email,
            @NotBlank @PasswordByteLength String password) {
    }

    /**
     * DTO para verificação de código multifator
     */
    public record MfaRequest(
        @NotBlank @Email @Size(max = UserUtils.EMAIL_MAX_LENGTH) String email,
        @NotBlank @Pattern(regexp = TwoFactorService.CODE_PATTERN, message = TwoFactorService.CODE_FORMAT_MESSAGE) String code
    ) {
    }

    /**
     * DTO com token JWT de autenticação
     * 
     * @param token Token de autenticação
     */
    public record MfaResponse(
        String token
    ) {
    }

    /**
     * DTO para alteração de senha do usuário autenticado
     * 
     * @param currentPassword Senha atual do usuário
     * @param newPassword     Nova senha do usuário
     */
    public record ChangePasswordRequest(
        @NotBlank @Size(max = PasswordPolicy.MAX_EXISTING_PASSWORD_LENGTH) String currentPassword,
        @NotBlank @PasswordByteLength
        @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE) String newPassword
    ) {
    }
}
