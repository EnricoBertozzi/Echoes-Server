package com.n0hana.echoes_server.infra.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RedisRepositoryTest {

    @Mock private StringRedisTemplate template;
    @Mock private ValueOperations<String, String> ops;

    private RedisRepository<String> repo;

    @BeforeEach
    void setUp() {
        repo = new RedisRepository<>(template, CodeType.PASSWORD_RESET, ValueCodec.string());
    }

    @Test
    @DisplayName("save usa prefixo do namespace e TTL padrão")
    void saveUsesPrefixAndDefaultTtl() {
        when(template.opsForValue()).thenReturn(ops);

        repo.save("a@b.com", "123456");

        verify(ops).set("password:code:a@b.com", "123456", Duration.ofMinutes(5));
    }

    @Test
    @DisplayName("save com TTL customizado ignora o padrão")
    void saveWithCustomTtl() {
        when(template.opsForValue()).thenReturn(ops);

        repo.save("x", "v", Duration.ofSeconds(30));

        verify(ops).set("password:code:x", "v", Duration.ofSeconds(30));
    }

    @Test
    @DisplayName("save rejeita TTL nulo, zero ou negativo sem tocar no Redis")
    void saveRejectsInvalidTtl() {
        assertThrows(IllegalArgumentException.class, () -> repo.save("x", "v", null));
        assertThrows(IllegalArgumentException.class, () -> repo.save("x", "v", Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> repo.save("x", "v", Duration.ofSeconds(-1)));
        verifyNoInteractions(template);
    }

    @Test
    @DisplayName("find devolve o valor decodificado ou vazio")
    void find() {
        when(template.opsForValue()).thenReturn(ops);
        when(ops.get("password:code:a")).thenReturn("111111");
        when(ops.get("password:code:b")).thenReturn(null);

        assertEquals(Optional.of("111111"), repo.find("a"));
        assertTrue(repo.find("b").isEmpty());
    }

    @Test
    @DisplayName("exists trata null do Redis como falso")
    void exists() {
        when(template.hasKey("password:code:a")).thenReturn(true);
        when(template.hasKey("password:code:b")).thenReturn(false);
        when(template.hasKey("password:code:c")).thenReturn(null);

        assertTrue(repo.exists("a"));
        assertFalse(repo.exists("b"));
        assertFalse(repo.exists("c"));
    }

    @Test
    @DisplayName("delete remove a chave prefixada")
    void delete() {
        repo.delete("a");

        verify(template).delete("password:code:a");
    }

    @Test
    @DisplayName("remainingTtl converte milissegundos e ignora chaves inexistentes/sem expiração")
    void remainingTtl() {
        when(template.getExpire("password:code:a", TimeUnit.MILLISECONDS)).thenReturn(1500L);
        when(template.getExpire("password:code:b", TimeUnit.MILLISECONDS)).thenReturn(-2L);
        when(template.getExpire("password:code:c", TimeUnit.MILLISECONDS)).thenReturn(-1L);

        assertEquals(Optional.of(Duration.ofMillis(1500)), repo.remainingTtl("a"));
        assertTrue(repo.remainingTtl("b").isEmpty());
        assertTrue(repo.remainingTtl("c").isEmpty());
    }

    @Test
    @DisplayName("namespaces diferentes não enxergam as chaves uns dos outros")
    void isolationBetweenNamespaces() {
        when(template.opsForValue()).thenReturn(ops);
        RedisRepository<String> mfa = new RedisRepository<>(template, CodeType.MFA, ValueCodec.string());

        mfa.save("a@b.com", "1");
        repo.save("a@b.com", "2");

        verify(ops).set("pending:login:a@b.com", "1", Duration.ofMinutes(5));
        verify(ops).set("password:code:a@b.com", "2", Duration.ofMinutes(5));
    }

    @SuppressWarnings("unchecked")
    private Cursor<String> cursorOf(String... keys) {
        Cursor<String> cursor = org.mockito.Mockito.mock(Cursor.class);
        java.util.Iterator<String> it = List.of(keys).iterator();
        org.mockito.Mockito.doAnswer(inv -> {
            java.util.function.Consumer<String> consumer = inv.getArgument(0);
            it.forEachRemaining(consumer);
            return null;
        }).when(cursor).forEachRemaining(any());
        return cursor;
    }

    @Test
    @DisplayName("findIdsByPrefix usa SCAN com o prefixo do namespace e devolve ids sem ele")
    void findIdsByPrefixScansNamespace() {
        RedisRepository<String> known = new RedisRepository<>(template, CodeType.KNOWN_DEVICE, ValueCodec.string());
        Cursor<String> cursor = cursorOf("known-device:u1:aaa", "known-device:u1:bbb");
        when(template.scan(any(ScanOptions.class))).thenReturn(cursor);

        assertEquals(List.of("u1:aaa", "u1:bbb"), known.findIdsByPrefix("u1:"));

        ArgumentCaptor<ScanOptions> options = ArgumentCaptor.forClass(ScanOptions.class);
        verify(template).scan(options.capture());
        assertEquals("known-device:u1:*", options.getValue().getPattern());
        verify(cursor).close();
    }

    @Test
    @DisplayName("findIdsByPrefix escapa curingas: o prefixo é literal")
    void prefixWildcardsAreEscaped() {
        assertEquals("a\\*b\\?c\\[d\\]e\\\\f", RedisRepository.escapeGlob("a*b?c[d]e\\f"));
    }

    @Test
    @DisplayName("deleteByPrefix apaga só as chaves encontradas")
    void deleteByPrefixDeletesFoundKeys() {
        RedisRepository<String> known = new RedisRepository<>(template, CodeType.KNOWN_DEVICE, ValueCodec.string());
        Cursor<String> cursor = cursorOf("known-device:u1:aaa", "known-device:u1:bbb");
        when(template.scan(any(ScanOptions.class))).thenReturn(cursor);
        when(template.delete(List.of("known-device:u1:aaa", "known-device:u1:bbb"))).thenReturn(2L);

        assertEquals(2L, known.deleteByPrefix("u1:"));
    }

    @Test
    @DisplayName("deleteByPrefix sem chaves não chama DEL")
    void deleteByPrefixWithoutKeysDoesNothing() {
        Cursor<String> cursor = cursorOf();
        when(template.scan(any(ScanOptions.class))).thenReturn(cursor);

        assertEquals(0L, repo.deleteByPrefix("nada:"));
        verify(template, never()).delete(any(java.util.Collection.class));
    }
}
