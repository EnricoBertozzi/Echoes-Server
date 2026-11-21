package com.n0hana.echoes_server.auth.attempt;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.n0hana.echoes_server.auth.exception.TooManyAttemptsException;

/** Bloqueio por força bruta contra Redis real: 5ª falha bloqueia e o TTL libera. */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIf("com.n0hana.echoes_server.support.RedisAvailability#redisReachable")
class AttemptServiceRedisIntegrationTest {

    @Autowired private AttemptService attempts;
    @Autowired private StringRedisTemplate redis;

    private final String email = "brute-" + UUID.randomUUID() + "@test.local";


    @AfterEach
    void cleanup() {
        attempts.clear(AttemptScope.LOGIN, email);
    }

    @Test
    @DisplayName("Login: 4 falhas não bloqueiam; a 5ª bloqueia (429)")
    void blocksOnFifthFailure() {
        for (int i = 1; i <= 4; i++) {
            assertFalse(attempts.register(AttemptScope.LOGIN, email), "falha " + i);
            assertDoesNotThrow(() -> attempts.assertNotBlocked(AttemptScope.LOGIN, email));
        }
        assertTrue(attempts.register(AttemptScope.LOGIN, email), "5ª falha atinge o limite");
        assertThrows(TooManyAttemptsException.class, () -> attempts.assertNotBlocked(AttemptScope.LOGIN, email));
    }

    @Test
    @DisplayName("A janela tem TTL de 15 min, não renova e libera sozinha ao expirar")
    void ttlWindowUnblocksAutomatically() throws Exception {
        for (int i = 0; i < 5; i++) attempts.register(AttemptScope.LOGIN, email);
        String key = CodeTypeKey.login(email);

        Long ttl = redis.getExpire(key, TimeUnit.SECONDS);
        assertTrue(ttl != null && ttl > 0 && ttl <= Duration.ofMinutes(15).toSeconds(), "ttl=" + ttl);

        // mais uma falha não renova a janela
        attempts.register(AttemptScope.LOGIN, email);
        Long after = redis.getExpire(key, TimeUnit.SECONDS);
        assertTrue(after <= ttl, "TTL não pode ser renovado");

        // simula a passagem do tempo encurtando o TTL real no Redis
        redis.expire(key, Duration.ofSeconds(1));
        Thread.sleep(1600);
        assertDoesNotThrow(() -> attempts.assertNotBlocked(AttemptScope.LOGIN, email));
    }

    /** prefixo real vem do CodeType; evita duplicar a string no teste */
    private static final class CodeTypeKey {
        static String login(String email) {
            return com.n0hana.echoes_server.infra.redis.CodeType.LOGIN_ATTEMPTS.getPrefix()
                    + email.trim().toLowerCase(java.util.Locale.ROOT);
        }
    }
}
