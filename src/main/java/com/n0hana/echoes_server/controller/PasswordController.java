package com.n0hana.echoes_server.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.n0hana.echoes_server.dto.PasswordDTO;
import com.n0hana.echoes_server.service.password.PasswordResetService;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;


@RestController
@RequestMapping("/api/password")
@RequiredArgsConstructor
public class PasswordController {

    private final PasswordResetService service;

    @PostMapping("/forgot")
    public ResponseEntity<PasswordDTO.ForgotResponse> forgotPassword(@RequestBody @Valid PasswordDTO.ForgotRequest dto) {
        
        service.requestReset(dto);
        return ResponseEntity.ok(
            new PasswordDTO.ForgotResponse("Caso o e-mail esteja cadastrado, você receberá o código em breve...")
        ); 
    }

    @PostMapping("/reset")
    public ResponseEntity<PasswordDTO.ResetResponse> resetPassword(@RequestBody @Valid PasswordDTO.ResetRequest dto) {
           
        try {
            service.resetPassword(dto);
            return ResponseEntity.ok(new PasswordDTO.ResetResponse("Senha alterado com sucesso."));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new PasswordDTO.ResetResponse("Não foi possível alterar a senha.\n " + e.getMessage()));
        }
    }
    
    @PostMapping("/validate")
    public ResponseEntity<PasswordDTO.ValidateResponse> validatePasswordToChange(
    HttpServletRequest request,
    HttpServletResponse response,
    @RequestBody @Valid PasswordDTO.ValidateRequest dto,
    @RequestHeader(value = "Authorization", required = false) String header
    ) {
        try {
            String token = null;

            // API → Authorization header
            if (header != null && header.startsWith("Bearer ")) {
                token = header.replace("Bearer ", "");
            }

            // Browser → Cookie
            if (token == null && request.getCookies() != null) {
                for (Cookie cookie : request.getCookies()) {
                    if (cookie.getName().equals("access_token")) {
                        token = cookie.getValue();
                        break;
                    }
                }
            }

            if (token == null) {
                return ResponseEntity.badRequest().build();
            }
            String changeToken = service.validatePassword(dto, token);
            return ResponseEntity.ok(new PasswordDTO.ValidateResponse(changeToken));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/change")
    public ResponseEntity<Void> changePassword(@RequestBody @Valid PasswordDTO.ChangeRequest dto) {
        try {
            service.changePassword(dto);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
        
    }
    
    
}
