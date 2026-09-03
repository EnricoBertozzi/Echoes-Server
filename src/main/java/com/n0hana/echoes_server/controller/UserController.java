package com.n0hana.echoes_server.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.n0hana.echoes_server.dto.DeleteAccountConfirmDTO;
import com.n0hana.echoes_server.dto.UserInfoResponseDTO;
import com.n0hana.echoes_server.dto.UserProfileExportDTO;
import com.n0hana.echoes_server.model.User;
import com.n0hana.echoes_server.service.auth.AuthService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final AuthService authService;

    @GetMapping("/me")
    public ResponseEntity<UserInfoResponseDTO> getUserInfo() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        return ResponseEntity.ok(new UserInfoResponseDTO(
            user.getName(),
            user.getEmail(),
            user.getRole().toString()
        ));
    }
    
    @GetMapping("/me/export")
    public ResponseEntity<UserProfileExportDTO> exportUserProfile() {
        // Recupera o usuário autenticado com total segurança do contexto do Spring Security
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        // Processa o mapeamento e gera o DTO limpo na camada de serviço
        UserProfileExportDTO exportData = authService.exportUserData(user);

        // Configura os cabeçalhos HTTP para instruir o cliente a realizar o download do arquivo físico
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setContentDispositionFormData("attachment", "echoes_dados_perfil.json");

        return ResponseEntity.ok()
                .headers(headers)
                .body(exportData);
    }
    
    @PostMapping("/me/delete/request")
    public ResponseEntity<Void> requestDeleteAccount() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        try {
            authService.requestDeleteAccount(user);
            return ResponseEntity.ok().build();
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/me/delete")
    public ResponseEntity<Void> confirmDeleteAccount(@RequestBody DeleteAccountConfirmDTO dto) {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        try {
            authService.confirmDeleteAccount(user, dto.code());
            return ResponseEntity.noContent().build();
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().build();
        }
    }
}
