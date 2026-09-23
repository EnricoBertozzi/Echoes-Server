package com.n0hana.echoes_server.auth.password;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.n0hana.echoes_server.auth.password.PasswordDTO.RequestReset;
import com.n0hana.echoes_server.auth.password.PasswordDTO.RequestForgot;
import com.n0hana.echoes_server.auth.password.PasswordDTO.ValidadeCode;

import jakarta.validation.Valid;

/**
 * Controler REST para alteração e redefinação de senha pelos usuários
 * 
 * @author Enrico Bertozzi
 * @since 0.1.1
 */
@RestController 
@RequestMapping("/api/v1/password")
public class PasswordController {

    @Autowired 
    private PasswordService passwordService;

    /**
     * Realiza a solicitação de reset de senha
     * 
     * @param dto Objeto com email para solicitação do request
     * 
     * @return {@link ResponseEntity} com código HTTP 204 (NO CONTENT)
     */
    @PostMapping("/forgot")
    public ResponseEntity<Void> request(@RequestBody @Valid RequestForgot dto) {
        passwordService.request(dto.email());
        return ResponseEntity.noContent().build();
    }

    /**
     * Completa o processos de redefinição de senha
     * 
     * @param dto Objeto com dados de redefinição de senha
     * @return {@link ResponseEntity} com código HTTP 204 (NO CONTENT)
     */
    @PostMapping("/reset")
    public ResponseEntity<Void> reset(@RequestBody @Valid RequestReset dto) {
        passwordService.reset(dto.email(), dto.code(), dto.newPassword());
        return ResponseEntity.noContent().build();
    }

    /**
     * Realiza validação do código multifator de redefinição de senha
     * 
     * @param dto Objeto com dados para verificação
     * 
     * @return {@link ResponseEntity} com código HTTP 204 (NO CONTENT)
     */
    @PostMapping("/validate")
    public ResponseEntity<Void> validadeCode(@RequestBody @Valid ValidadeCode dto) {
        passwordService.validate(dto.email(), dto.code());
        return ResponseEntity.noContent().build();
    }
}
