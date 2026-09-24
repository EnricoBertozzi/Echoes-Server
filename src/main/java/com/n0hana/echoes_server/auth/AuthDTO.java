package com.n0hana.echoes_server.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
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
            @NotBlank @Email String email,
            @NotBlank String password) {
    }

    /**
     * DTO para verificação de código multifator
     */
    public record MfaRequest(
        @Email String email,
        @Size(min = 6, max = 6) String code
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
}
