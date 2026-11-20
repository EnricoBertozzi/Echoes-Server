package com.n0hana.echoes_server.user.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.n0hana.echoes_server.infra.security.SecurityConfig;
import com.n0hana.echoes_server.user.dto.UserEmailDTO;
import com.n0hana.echoes_server.user.service.UserInfoService;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;

/**
 * RestController com o endpoint auditado para leitura do e-mail completo de um
 * usuário.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@SecurityRequirement(name = SecurityConfig.SECURITY)
public class UserEmailController {

    private final UserInfoService userInfoService;

    /**
     * Endpoint para consulta do e-mail completo de um usuário. Cada chamada
     * gera um registro de auditoria VIEW_EMAIL.
     */
    @GetMapping("/{id}/email")
    @PreAuthorize("@guard.canAccess(#id)")
    public ResponseEntity<UserEmailDTO> findEmail(@PathVariable UUID id) {
        return ResponseEntity.ok(userInfoService.findEmail(id));
    }
}
