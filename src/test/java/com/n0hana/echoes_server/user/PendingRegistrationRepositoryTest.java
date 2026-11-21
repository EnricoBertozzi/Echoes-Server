package com.n0hana.echoes_server.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import com.n0hana.echoes_server.infra.RedisConfig;
import com.n0hana.echoes_server.infra.redis.RedisRepositoryFactory;

class PendingRegistrationRepositoryTest {

    private static final String KEY = "pending:registration:joao@example.com";

    private StringRedisTemplate template;
    private ValueOperations<String, String> ops;
    private ZSetOperations<String, String> zset;
    private PendingRegistrationRepository repository;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        template = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        zset = mock(ZSetOperations.class);
        when(template.opsForValue()).thenReturn(ops);
        when(template.opsForZSet()).thenReturn(zset);
        repository = new PendingRegistrationRepository(new RedisRepositoryFactory(
                template, new RedisConfig().redisTemplate(mock(RedisConnectionFactory.class))), template);
    }

    private static PendingRegistration pending(Instant createdAt) {
        return new PendingRegistration("João", "joao@example.com", "STUDENT", UUID.randomUUID(),
                "123456", createdAt.plusSeconds(300), 0, createdAt);
    }

    @Test
    @DisplayName("save grava com retenção de 7 dias (mesmo prazo do convite) e indexa o e-mail")
    void saveUsesInviteRetention() {
        PendingRegistration p = pending(Instant.now());

        assertEquals(p, repository.save(p));

        verify(ops).set(eq(KEY), anyString(), eq(Duration.ofDays(7)));
        verify(zset).add(eq(PendingRegistrationRepository.INDEX_KEY), eq("joao@example.com"), anyDouble());
    }

    @Test
    @DisplayName("listActive devolve só convites que ainda existem e remove o índice dos expirados")
    void listActiveDropsExpiredEntries() {
        PendingRegistration vivo = pending(Instant.now());
        when(zset.range(PendingRegistrationRepository.INDEX_KEY, 0, -1))
                .thenReturn(java.util.Set.of("joao@example.com", "expirado@example.com"));

        PendingRegistrationRepository spy = org.mockito.Mockito.spy(repository);
        org.mockito.Mockito.doReturn(java.util.Optional.of(vivo)).when(spy).findByEmail("joao@example.com");
        org.mockito.Mockito.doReturn(java.util.Optional.empty()).when(spy).findByEmail("expirado@example.com");

        var ativos = spy.listActive();

        assertEquals(1, ativos.size());
        assertEquals("joao@example.com", ativos.get(0).email());
        verify(zset).removeRangeByScore(eq(PendingRegistrationRepository.INDEX_KEY), eq(0.0), anyDouble());
        verify(zset).remove(PendingRegistrationRepository.INDEX_KEY, "expirado@example.com");
    }

    @Test
    @DisplayName("savePreservingTtl grava só com o tempo restante, sem estender a janela")
    void savePreservingTtlUsesRemaining() {
        repository.savePreservingTtl(pending(Instant.now().minus(Duration.ofDays(7)).plus(Duration.ofMinutes(5))));

        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
        verify(ops).set(eq(KEY), anyString(), ttl.capture());
        assertTrue(ttl.getValue().compareTo(Duration.ofMinutes(5)) <= 0);
        assertTrue(ttl.getValue().compareTo(Duration.ofMinutes(4)) > 0);
    }

    @Test
    @DisplayName("savePreservingTtl não grava registro cuja retenção já terminou")
    void savePreservingTtlSkipsExpired() {
        repository.savePreservingTtl(pending(Instant.now().minus(Duration.ofDays(7)).minus(Duration.ofMinutes(1))));

        verify(ops, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("findByEmail devolve o registro gravado; deleteByEmail remove a chave")
    void findAndDelete() {
        PendingRegistration p = pending(Instant.now());
        repository.save(p);
        ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);
        verify(ops).set(eq(KEY), stored.capture(), any(Duration.class));
        when(ops.get(KEY)).thenReturn(stored.getValue());

        assertEquals(p, repository.findByEmail("joao@example.com").orElseThrow());

        repository.deleteByEmail("joao@example.com");
        verify(template).delete(KEY);
    }

    @Test
    @DisplayName("findByEmail vazio quando a chave não existe")
    void findMissing() {
        assertTrue(repository.findByEmail("x@y.com").isEmpty());
    }
}
