package com.n0hana.echoes_server.auth;

import java.security.Principal;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.n0hana.echoes_server.auth.AuthDTO.ChangePasswordRequest;
import com.n0hana.echoes_server.auth.AuthDTO.LoginRequest;
import com.n0hana.echoes_server.auth.AuthDTO.MfaRequest;
import com.n0hana.echoes_server.auth.AuthDTO.MfaResponse;
import com.n0hana.echoes_server.infra.security.SecurityConfig;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;


/**
 * Controler REST para gerenciar autenticação e autorização de usuários no sistema
 * 
 * @author Enrico Bertozzi
 * @since 0.1.1
 * @see {@link AuthService}
 */
@RestController 
@RequestMapping("/api/v1/auth")
public class AuthController {
    
    private final AuthService service;

    AuthController(AuthService service) {
        this.service = service;
    }

    /**
     * Realiza a verificação do login do usuário
     * 
     * @param dto Objeto com email e senha do usuário para validação
     * @return {@link ResponseEntity} com o código HTTP 204 (NO CONTENT)
     */
    @PostMapping("/login")
    public ResponseEntity<Void> login(@RequestBody @Valid LoginRequest dto) {
        service.login(dto.email(), dto.password());
        return ResponseEntity.noContent().build();
    }

    /**
     * Realiza a verificação do código multifator de login
     * 
     * @param dto Objeto com dados para verificação
     * @return {@link ResponseEntity} com token JWT de autenticação e com o código HTTP 200 (OK)
     */
    @PostMapping("/mfa")
    public ResponseEntity<MfaResponse> verifyTwoFactor(@RequestBody @Valid MfaRequest dto) {
        String token = service.verifyMfaCode(dto.email(), dto.code());
        return ResponseEntity.ok(new MfaResponse(token));
    }

    /**
     * Altera a senha do usuário autenticado.
     * 
     * @param dto Objeto com a senha atual e a nova senha para validação
     * @param principal Identificação do usuário autenticado
     * @return {@link ResponseEntity} com o código HTTP 200 (OK)
     */
    @PatchMapping("/password/change")
    public ResponseEntity<Void> changePassword(@RequestBody @Valid ChangePasswordRequest dto, Principal principal) {
        service.changePassword(principal.getName(), dto.currentPassword(), dto.newPassword());
        return ResponseEntity.ok().build();
    }

    /**
     * Realiza o logout do usuário, revogando os tokens ativos.
     * 
     * @return {@link ResponseEntity} com o código HTTP 204 (NO CONTENT)
     */
    @PostMapping("/logout")
    @SecurityRequirement(name = SecurityConfig.SECURITY)
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        service.logout(request.getHeader("Authorization"));
        return ResponseEntity.noContent().build();
    }
    
}
