package com.n0hana.echoes_server.term;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.n0hana.echoes_server.auth.session.SessionCookie;
import com.n0hana.echoes_server.infra.security.JwtBlackListRepository;
import com.n0hana.echoes_server.infra.security.JwtTokenService;
import com.n0hana.echoes_server.term.dto.CreateTermRequestDTO;
import com.n0hana.echoes_server.term.model.DocumentType;
import com.n0hana.echoes_server.term.model.TermStatus;
import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.model.Teacher;

import jakarta.servlet.http.Cookie;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:term-revoke;DB_CLOSE_DELAY=-1;MODE=MySQL")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TermRevokeIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TermService terms;
    @Autowired private UserRepository users;
    @Autowired private UserTermAcceptanceRepository acceptances;
    @Autowired private JwtTokenService jwt;

    @MockitoBean private JwtBlackListRepository jwtBlackList;

    @Test
    void revokesBrowserSessionAndRequiresNewAcceptance() throws Exception {
        Teacher teacher = new Teacher();
        teacher.setEmail("revoke@test.local");
        teacher.setName("Docente");
        teacher.setPassword("hash");
        var user = users.save(teacher);
        var term = terms.createTerm(new CreateTermRequestDTO(
                "1.0.0", "Termos de uso", DocumentType.TERMS_OF_USE, TermStatus.PUBLISHED));
        var original = terms.acceptTerms(user.getId(), term.getType());
        var session = new Cookie(SessionCookie.NAME, jwt.generate(user));
        String otherSession = "Bearer " + jwt.generate(user);

        mockMvc.perform(post("/terms/revoke").cookie(session)
                .header(SessionCookie.CLIENT_HEADER, "web"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().value(SessionCookie.NAME, ""))
                .andExpect(cookie().maxAge(SessionCookie.NAME, 0));

        assertNotNull(acceptances.findById(original.getId()).orElseThrow().getRevokedAt());
        mockMvc.perform(get("/api/v1/users/me").cookie(session))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", otherSession))
                .andExpect(status().isUnauthorized());

        // O JWT registra a emissão em segundos inteiros.
        Instant cutoff = users.findById(user.getId()).orElseThrow().getTokensRevokedAt();
        long nextSecondMillis = (cutoff.getEpochSecond() + 1) * 1000;
        Thread.sleep(Math.max(0, nextSecondMillis - System.currentTimeMillis()));
        var newSession = new Cookie(SessionCookie.NAME, jwt.generate(user));

        mockMvc.perform(get("/terms/pending").cookie(newSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("TERMS_OF_USE"));
        mockMvc.perform(get("/animals").param("page", "0").param("size", "10").cookie(newSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.pendingTerms[0]").value("TERMS_OF_USE"));
        mockMvc.perform(post("/terms/accept").cookie(newSession)
                .header(SessionCookie.CLIENT_HEADER, "web")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"TERMS_OF_USE\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/terms/pending").cookie(newSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/animals").param("page", "0").param("size", "10").cookie(newSession))
                .andExpect(status().isOk());

        var current = acceptances.findByUserIdAndTermsId(user.getId(), term.getId()).orElseThrow();
        assertNull(current.getRevokedAt());
        assertNotNull(acceptances.findById(original.getId()).orElseThrow().getRevokedAt());
    }
}
