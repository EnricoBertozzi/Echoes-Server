package com.n0hana.echoes_server.infra.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.n0hana.echoes_server.infra.RedisConfig;

class RedisCodeStoreTest {

    private StringRedisTemplate template;
    private ValueOperations<String, String> ops;
    private RedisCodeStore store;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        template = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(ops);
        store = new RedisCodeStore(new RedisRepositoryFactory(
                template, new RedisConfig().redisTemplate(mock(RedisConnectionFactory.class))));
    }

    @Test
    @DisplayName("save usa prefixo e TTL do CodeType")
    void saveDefaultTtl() {
        store.save("a@b.com", "123456", CodeType.MFA);

        verify(ops).set("pending:login:a@b.com", "123456", Duration.ofMinutes(15));
    }

    @Test
    @DisplayName("save com TTL customizado (blacklist de JWT)")
    void saveCustomTtl() {
        store.save("jti", "revoked", CodeType.JWT_BLACKLIST, Duration.ofMinutes(7));

        verify(ops).set("blacklist:token:jti", "revoked", Duration.ofMinutes(7));
    }

    @Test
    @DisplayName("get, isExists e delete usam a chave prefixada")
    void getExistsDelete() {
        when(ops.get("password:token:a")).thenReturn("tok");
        when(template.hasKey("password:token:a")).thenReturn(true);

        assertEquals(Optional.of("tok"), store.get("a", CodeType.PASSWORD_TOKEN));
        assertTrue(store.isExists("a", CodeType.PASSWORD_TOKEN));
        store.delete("a", CodeType.PASSWORD_TOKEN);

        verify(template).delete("password:token:a");
    }

    @Test
    @DisplayName("getExpire devolve segundos restantes, ou -1 sem TTL")
    void getExpire() {
        when(template.getExpire("registration:a", TimeUnit.MILLISECONDS)).thenReturn(90_000L);
        when(template.getExpire("registration:b", TimeUnit.MILLISECONDS)).thenReturn(-2L);

        assertEquals(90L, store.getExpire("a", CodeType.INVITE));
        assertEquals(-1L, store.getExpire("b", CodeType.INVITE));
    }
}
