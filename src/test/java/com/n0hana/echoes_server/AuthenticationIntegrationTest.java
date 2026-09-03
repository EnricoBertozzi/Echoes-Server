package com.n0hana.echoes_server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class AuthenticationIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("Deve tentar realizar login com usuário inexistente e retornar 401 Unauthorized")
    void deveRetornarUnauthorizedAoTentarLogarComUsuarioInexistente() throws Exception {
        String loginJson = """
                {
                    "email": "santana@example.com",
                    "password": "SenhaSegura123"
                }
                """;

        // Executa a requisição usando o mockMvc inicializado na classe base
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson))
                .andExpect(status().isUnauthorized()); 
    }
}
