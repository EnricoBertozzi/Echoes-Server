package com.n0hana.echoes_server.auth;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

@WebMvcTest(AuthController.class)
@ActiveProfiles("test")
@WithMockUser
@AutoConfigureMockMvc(addFilters = false)
public class AuthControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService service;

    @Test
    @DisplayName("POST /api/v1/auth/login com dados validos para login (200 OK)")
    void shouldSendValidLoginData() throws Exception {
        // Arrange
        String payload = """
                {
                    "email": "teste@email.com",
                    "password": "senhaSegura123"
                } """;
        doNothing().when(service).login(anyString(), anyString());

        // Act e Assert
        mockMvc.perform(post("/api/v1/auth/login")
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
                } """;

        // Act e Assert
        mockMvc.perform(post("/api/v1/auth/login")
                .content(payload)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }
}