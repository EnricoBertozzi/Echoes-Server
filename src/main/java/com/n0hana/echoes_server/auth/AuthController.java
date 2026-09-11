package com.n0hana.echoes_server.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.n0hana.echoes_server.auth.dto.AuthRequestDTO;
import com.n0hana.echoes_server.auth.dto.AuthResponseDTO;
import com.n0hana.echoes_server.auth.dto.AuthFailedResponseDTO;
import com.n0hana.echoes_server.mfa.VerifyDTO;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequestDTO dto) {
        try {
            authService.loginRequest(dto);
            return ResponseEntity.ok().build();
        } catch (RuntimeException ex) {
            AuthFailedResponseDTO body = new AuthFailedResponseDTO(
                ex.getMessage(),
                0,
                0
            );

            return ResponseEntity.badRequest().body(body);
        }
    }

    @PostMapping("/login/2fa")
    public ResponseEntity<?> loginMFA(@RequestBody VerifyDTO dto, HttpServletRequest request, HttpServletResponse response) {
        try {
            String jwt = authService.login2fa(dto);
            String accept = request.getHeader("Accept");

            // BROWSER → COOKIE
            Cookie cookie = new Cookie("access_token", jwt);
            cookie.setHttpOnly(true);
            cookie.setSecure(false);
            cookie.setPath("/");
            cookie.setMaxAge(60 * 15);
            response.addCookie(cookie);

            return ResponseEntity.ok(new AuthResponseDTO(jwt));
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().build();
        }
    }   

    @GetMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response, @RequestHeader(value = "Authorization", required = false) String header) {
        try {
            authService.logout(request, response, header);
            return ResponseEntity.ok().build();
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/validate-token")
    public ResponseEntity<Void> authMe() {
        return ResponseEntity.ok().build();
    }
}
