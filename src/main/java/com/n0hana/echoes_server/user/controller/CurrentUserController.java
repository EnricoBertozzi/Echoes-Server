package com.n0hana.echoes_server.user.controller;

import java.security.Principal;

import org.apache.catalina.connector.Response;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.n0hana.echoes_server.user.dto.UserDTO;
import com.n0hana.echoes_server.user.service.UserInfoService;

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

    /**
     * Endpoint para consulta de dados do usuário autenticado
     */
    @GetMapping("/me")
    public ResponseEntity<UserDTO> me(Principal principal) {
        UserDTO dto = userInfoService.findUserByEmail(principal.getName());
        return ResponseEntity.ok(dto);
    }
}
