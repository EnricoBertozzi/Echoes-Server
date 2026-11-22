package com.n0hana.echoes_server.infra.security;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.n0hana.echoes_server.auth.password.PasswordService;
import com.n0hana.echoes_server.auth.password.PasswordTokenRepository;
import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.model.Student;
import com.n0hana.echoes_server.user.model.User;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIf("com.n0hana.echoes_server.support.RedisAvailability#redisReachable")
class JwtRevocationIntegrationTest {

    @Autowired private JwtFilter filter;
    @Autowired private JwtTokenService jwt;
    @Autowired private UserRepository users;
    @Autowired private PasswordService passwordService;
    @Autowired private PasswordTokenRepository passwordTokens;

    private User user;


    @BeforeEach
    void setUp() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        Student s = new Student();
        s.setEmail("jwt-" + UUID.randomUUID() + "@test.local");
        s.setName("Jwt");
        s.setPassword("hash");
        user = users.save(s);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        passwordTokens.delete(user.getEmail());
        users.delete(user);
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    private boolean authenticates(String token) throws Exception {
        SecurityContextHolder.clearContext();
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/users/me");
        req.addHeader("Authorization", "Bearer " + token);
        filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());
        return SecurityContextHolder.getContext().getAuthentication() != null;
    }

    private void setCutoff(Instant passwordChangedAt, Instant tokensRevokedAt) {
        User u = users.findById(user.getId()).orElseThrow();
        u.setPasswordChangedAt(passwordChangedAt);
        u.setTokensRevokedAt(tokensRevokedAt);
        users.save(u);
    }

    private static void sleepToNextSecond() throws InterruptedException {
        long ms = 1000 - (System.currentTimeMillis() % 1000);
        Thread.sleep(ms + 50);
    }

    @Test
    @DisplayName("Token sem revogação autentica")
    void validTokenAuthenticates() throws Exception {
        org.junit.jupiter.api.Assertions.assertTrue(authenticates(jwt.generate(user)));
    }

    @Test
    @DisplayName("Token emitido antes de changePassword/reset/revogação é rejeitado")
    void rejectsTokensIssuedBeforeCutoff() throws Exception {
        String token = jwt.generate(user);
        Instant iat = jwt.issuedAt(token);

        setCutoff(iat.plusSeconds(5), null);
        org.junit.jupiter.api.Assertions.assertFalse(authenticates(token), "passwordChangedAt posterior");

        setCutoff(null, iat.plusSeconds(5));
        org.junit.jupiter.api.Assertions.assertFalse(authenticates(token), "tokensRevokedAt posterior");

        setCutoff(iat.minusSeconds(5), iat.minusSeconds(5));
        org.junit.jupiter.api.Assertions.assertTrue(authenticates(token), "cutoff anterior ao iat não revoga");
    }

    @Test
    @DisplayName("PasswordService.reset invalida imediatamente o JWT anterior")
    void resetInvalidatesPreviousJwt() throws Exception {
        String token = jwt.generate(user);
        org.junit.jupiter.api.Assertions.assertTrue(authenticates(token));

        passwordTokens.save(user.getEmail(), "reset-token");
        passwordService.reset(user.getEmail(), "reset-token", "NovaSenha123!Aa");

        org.junit.jupiter.api.Assertions.assertFalse(authenticates(token));
    }

    @Test
    @DisplayName("Logout (blacklist) invalida o token")
    void logoutBlacklistsToken() throws Exception {
        String token = jwt.generate(user);
        org.junit.jupiter.api.Assertions.assertTrue(authenticates(token));

        jwt.invalidate("Bearer " + token);

        org.junit.jupiter.api.Assertions.assertFalse(authenticates(token));
    }

    @Test
    @DisplayName("Mesmo segundo (documenta o comportamento atual): iat <= cutoff truncado ao segundo é rejeitado")
    void sameSecondBoundary() throws Exception {
        String token = jwt.generate(user);
        Instant iat = jwt.issuedAt(token);
        assertNotNull(iat);
        org.junit.jupiter.api.Assertions.assertEquals(0, iat.getNano(), "iat em segundos inteiros");

        // revogação com precisão de ms dentro do MESMO segundo do iat: rejeita (fail-closed)
        setCutoff(iat.plusMillis(900), null);
        org.junit.jupiter.api.Assertions.assertFalse(authenticates(token), "mesmo segundo é rejeitado");

        // revogação no segundo imediatamente anterior ao iat: aceita
        setCutoff(iat.minusMillis(1), null);
        org.junit.jupiter.api.Assertions.assertTrue(authenticates(token), "segundo anterior é aceito");
        org.junit.jupiter.api.Assertions.assertEquals(
                iat.minusSeconds(1), iat.minusMillis(1).truncatedTo(ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("Fluxo real: login no mesmo segundo do reset é bloqueado; no segundo seguinte é aceito")
    void loginAfterResetSameVsNextSecond() throws Exception {
        sleepToNextSecond(); // início de um segundo, para o reset e o login caírem juntos
        passwordTokens.save(user.getEmail(), "reset-token");
        passwordService.reset(user.getEmail(), "reset-token", "NovaSenha123!Aa");
        String sameSecond = jwt.generate(user);
        Instant cutoff = users.findById(user.getId()).orElseThrow().getPasswordChangedAt();
        boolean sameSecondAsCutoff = jwt.issuedAt(sameSecond).equals(cutoff.truncatedTo(ChronoUnit.SECONDS));

        if (sameSecondAsCutoff) {
            org.junit.jupiter.api.Assertions.assertFalse(authenticates(sameSecond),
                    "novo login no mesmo segundo do reset é recusado hoje");
        }

        sleepToNextSecond();
        org.junit.jupiter.api.Assertions.assertTrue(authenticates(jwt.generate(user)),
                "login no segundo seguinte é aceito");
    }
}
