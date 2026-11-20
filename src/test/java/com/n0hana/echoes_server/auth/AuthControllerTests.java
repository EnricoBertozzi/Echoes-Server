package com.n0hana.echoes_server.auth;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.n0hana.echoes_server.infra.security.JwtTokenService;
import com.n0hana.echoes_server.user.UserRepository;

@WebMvcTest(AuthController.class)
@ActiveProfiles("test")
@WithMockUser
@AutoConfigureMockMvc
@org.springframework.context.annotation.Import(com.n0hana.echoes_server.auth.session.SessionCookie.class)
public class AuthControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService service;

    @MockitoBean
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    @DisplayName("POST /api/v1/auth/login com dados validos para login (200 OK)")
    void shouldSendValidLoginData() throws Exception {
        // Arrange
        String payload = """
                {
                    "email": "teste@email.com",
                    "password": "senhaSegura123"
                }
            """;
        doNothing().when(service).login(anyString(), anyString());

        // Act e Assert
        mockMvc.perform(post("/api/v1/auth/login")
                .with(csrf())
                .content(payload)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST /api/v1/auth/login com dados invalidos para login (404 BAD REQUEST)")
    void shouldThrowAuthFailedExceptionWhenSendInvalidLoginData() throws Exception {
        // Arrange
        String payload = """
                {
                    "email": "teste.email.com",
                    "password": "123456"
                } 
            """;

        // Act e Assert
        mockMvc.perform(post("/api/v1/auth/login")
                .with(csrf())
                .content(payload)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH /api/v1/auth/password/change altera a senha do usuário autenticado (200 OK)")
    void shouldChangePassword() throws Exception {
        // Arrange
        String payload = """
                {
                  "currentPassword": "senhaAtual",
                  "newPassword": "NovaSenha@123"
                }
            """;

        doNothing().when(service).changePassword(anyString(), anyString(), anyString());

        // Act e Assert
        mockMvc.perform(patch("/api/v1/auth/password/change")
                .with(user("usuario@email.com"))
                .with(csrf())
                .content(payload)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());

        // Assert
        verify(service).changePassword(anyString(), anyString(), anyString());
    }
}
