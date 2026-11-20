package com.n0hana.echoes_server.user.controller;

import java.security.Principal;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.n0hana.echoes_server.user.dto.UpdateUserDTO;
import com.n0hana.echoes_server.user.dto.UserDTO;
import com.n0hana.echoes_server.user.export.UserDataExportDTO;
import com.n0hana.echoes_server.user.export.UserDataExportService;
import com.n0hana.echoes_server.user.service.UserInfoService;
import com.n0hana.echoes_server.user.service.UserManagerService;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * RestController com implementação de endpoint para verificação de dados do
 * usuário autenticado
 * 
 * @author Enrico Bertozzi
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class CurrentUserController {

    private final UserInfoService userInfoService;
    private final UserManagerService userManagerService;
    private final UserDataExportService userDataExportService;

    /**
     * Endpoint para consulta de dados do usuário autenticado
     */
    @GetMapping("/me")
    public ResponseEntity<UserDTO> me(Principal principal) {
        UserDTO dto = userInfoService.findUserByEmail(principal.getName());
        return ResponseEntity.ok(dto);
    }

    @PatchMapping("/me")
    public ResponseEntity<UserDTO> updateMe(@RequestBody @Valid UpdateUserDTO dto, Principal principal) {
        UserDTO responseDto = userManagerService.updateCurrentUser(principal.getName(), dto);
        return ResponseEntity.ok(responseDto);
    }

    /**
     * Exporta todos os dados vinculados ao usuário autenticado (RF18 / LGPD art. 18).
     */
    @GetMapping("/me/export")
    public ResponseEntity<UserDataExportDTO> exportMe(Principal principal) {
        UserDataExportDTO data = userDataExportService.export(principal.getName());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"meus-dados-echoes.json\"")
                .body(data);
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMe(Principal principal) {
        userManagerService.deleteCurrentUser(principal.getName());
        return ResponseEntity.noContent().build();
    }
}
