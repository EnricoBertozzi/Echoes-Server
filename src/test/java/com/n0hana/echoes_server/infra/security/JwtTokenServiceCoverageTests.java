package com.n0hana.echoes_server.infra.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.n0hana.echoes_server.auth.exception.AuthFailedException;
import com.n0hana.echoes_server.user.model.Admin;

@DisplayName("JwtTokenService: emissão, validação e revogação")
class JwtTokenServiceCoverageTests {

    private final String secret = UUID.randomUUID() + "-" + UUID.randomUUID();
    private JwtBlackListRepository blacklist;
    private JwtTokenService service;
    private Admin user;

    @BeforeEach
    void setUp() {
        blacklist = mock(JwtBlackListRepository.class);
        service = new JwtTokenService(blacklist);
        ReflectionTestUtils.setField(service, "secret", secret);
        user = new Admin();
        user.setId(UUID.randomUUID());
    }

    private String foreignToken(String issuer, String otherSecret) {
        return JWT.create().withIssuer(issuer).withSubject("x").withJWTId(UUID.randomUUID().toString())
                .withExpiresAt(Instant.now().plusSeconds(600)).sign(Algorithm.HMAC256(otherSecret));
    }

    @Test
    @DisplayName("validateSecret recusa segredo nulo ou curto e aceita 32 bytes")
    void validateSecret() {
        JwtTokenService s = new JwtTokenService(blacklist);
        assertThrows(IllegalStateException.class, () -> s.validateSecret());
        ReflectionTestUtils.setField(s, "secret", "curto");
        assertThrows(IllegalStateException.class, () -> s.validateSecret());
        ReflectionTestUtils.setField(s, "secret", "x".repeat(32));
        assertDoesNotThrow(() -> s.validateSecret());
    }

    @Test
    @DisplayName("generate emite token com subject, iat e expiração de 2 h")
    void generate() {
        String token = service.generate(user);

        assertEquals(user.getId().toString(), service.validate(token));
        Instant iat = service.issuedAt(token);
        assertNotNull(iat);
        var decoded = JWT.decode(token);
        assertEquals(Duration.ofHours(2), Duration.between(decoded.getIssuedAtAsInstant(),
                decoded.getExpiresAtAsInstant()).minusSeconds(0));
    }

    @Test
    @DisplayName("validate e issuedAt recusam token malformado, com outro segredo ou outro emissor")
    void validateRecusa() {
        for (String bad : new String[] { "lixo", foreignToken("echoes-server", "outro-segredo-" + secret),
                foreignToken("outro-emissor", secret) }) {
            assertThrows(AuthFailedException.class, () -> service.validate(bad));
            assertThrows(AuthFailedException.class, () -> service.issuedAt(bad));
        }
    }

    @Test
    @DisplayName("issuedAt devolve null para token sem claim iat")
    void issuedAtAusente() {
        String semIat = foreignToken("echoes-server", secret);

        assertNull(service.issuedAt(semIat));
    }

    @Test
    @DisplayName("recoverToken extrai o Bearer e devolve null nos demais casos")
    void recoverToken() {
        assertEquals("abc", service.recoverToken("Bearer abc"));
        assertNull(service.recoverToken(null));
        assertNull(service.recoverToken("Basic abc"));
    }

    @Test
    @DisplayName("invalidate coloca o jti na blacklist pelo tempo restante")
    void invalidate() {
        String token = service.generate(user);
        String jti = JWT.decode(token).getId();

        service.invalidate("Bearer " + token);

        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
        verify(blacklist).save(org.mockito.ArgumentMatchers.eq(jti), ttl.capture());
        assertTrue(ttl.getValue().toMinutes() >= 118 && ttl.getValue().toMinutes() <= 120);
    }

    @Test
    @DisplayName("invalidate sem Bearer ou com token inválido falha com AuthFailedException")
    void invalidateRecusa() {
        assertThrows(AuthFailedException.class, () -> service.invalidate(null));
        assertThrows(AuthFailedException.class, () -> service.invalidate("Bearer lixo"));
        verify(blacklist, never()).save(anyString(), any());
    }

    @Test
    @DisplayName("isRevoked consulta a blacklist e trata token inválido como revogado")
    void isRevoked() {
        String token = service.generate(user);
        String jti = JWT.decode(token).getId();

        assertFalse(service.isRevoked(token));
        when(blacklist.exists(jti)).thenReturn(true);
        assertTrue(service.isRevoked(token));
        assertTrue(service.isRevoked("lixo"));
    }
}
