package com.n0hana.echoes_server.user.controller;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.n0hana.echoes_server.user.dto.PendingInvitationDTO;
import com.n0hana.echoes_server.user.service.UserRegisterService;

import lombok.RequiredArgsConstructor;

/**
 * Convites de cadastro pendentes (Redis), para a tela de membros e de admins.
 */
@RestController
@RequestMapping("/api/v1/users/pending")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
public class PendingInvitationController {

    private final UserRegisterService service;

    /**
     * GET /api/v1/users/pending?institutionId= — admin vê todos; gestor só os da
     * própria instituição.
     */
    @GetMapping
    public ResponseEntity<List<PendingInvitationDTO>> list(
            @RequestParam(required = false) UUID institutionId,
            Principal principal) {
        return ResponseEntity.ok(service.listPending(principal.getName(), institutionId));
    }
}
