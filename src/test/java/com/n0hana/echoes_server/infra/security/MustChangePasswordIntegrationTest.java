package com.n0hana.echoes_server.infra.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.model.Admin;
import com.n0hana.echoes_server.user.model.User;

/** RF07: com a flag ligada só a troca de senha funciona; depois dela o acesso volta (cadeia real de filtros). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnabledIf("com.n0hana.echoes_server.support.RedisAvailability#redisReachable")
class MustChangePasswordIntegrationTest {

    private static final String FIRST_PASSWORD = "Primeira@123";
    private static final String NEW_PASSWORD = "NovaSenha@456";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository users;
    @Autowired private JwtTokenService jwt;
    @Autowired private PasswordEncoder encoder;

    private User admin;

    @BeforeEach
    void setUp() {
        Admin a = new Admin();
        a.setEmail("first-" + UUID.randomUUID() + "@test.local");
        a.setName("Primeiro Acesso");
        a.setPassword(encoder.encode(FIRST_PASSWORD));
        a.setMustChangePassword(true);
        admin = users.save(a);
    }

    @AfterEach
    void tearDown() {
        users.delete(users.findById(admin.getId()).orElse(admin));
    }

    private String bearer() {
        return "Bearer " + jwt.generate(users.findById(admin.getId()).orElseThrow());
    }

    @Test
    @DisplayName("Primeiro acesso: /users/me sinaliza a flag e as demais rotas respondem 403")
    void flagBlocksEverythingButPasswordChange() throws Exception {
        String token = bearer();

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true));

        mockMvc.perform(get("/animals").header("Authorization", token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
    }

    @Test
    @DisplayName("Trocar para a mesma senha é recusado e a flag continua ligada")
    void sameSecondPasswordIsRejected() throws Exception {
        mockMvc.perform(patch("/api/v1/auth/password/change")
                .header("Authorization", bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + FIRST_PASSWORD + "\",\"newPassword\":\"" + FIRST_PASSWORD + "\"}"))
                .andExpect(status().isBadRequest());

        assertTrue(users.findById(admin.getId()).orElseThrow().isMustChangePassword());
    }

    @Test
    @DisplayName("Depois da troca a flag some e as rotas voltam a responder (sem o bloqueio)")
    void changePasswordClearsTheFlag() throws Exception {
        mockMvc.perform(patch("/api/v1/auth/password/change")
                .header("Authorization", bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + FIRST_PASSWORD + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isOk());

        assertFalse(users.findById(admin.getId()).orElseThrow().isMustChangePassword());

        // token novo (o anterior foi revogado pela troca)
        Thread.sleep(1100);
        MvcResult result = mockMvc.perform(get("/api/v1/users/me").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(false))
                .andReturn();

        String animals = mockMvc.perform(get("/animals").header("Authorization", bearer()))
                .andReturn().getResponse().getContentAsString();
        assertFalse(animals.contains("PASSWORD_CHANGE_REQUIRED"), result.getResponse().getContentAsString());
    }
}
