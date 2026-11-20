package com.n0hana.echoes_server.user;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import com.n0hana.echoes_server.infra.RedisConfig;
import com.n0hana.echoes_server.infra.redis.RedisRepositoryFactory;

class PendingRegistrationRepositoryCoverageTests {

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("listActive devolve lista vazia quando o índice do Redis retorna nulo")
    void listActiveWithNullIndex() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        ZSetOperations<String, String> zset = mock(ZSetOperations.class);
        when(template.opsForValue()).thenReturn(ops);
        when(template.opsForZSet()).thenReturn(zset);
        when(zset.range(PendingRegistrationRepository.INDEX_KEY, 0, -1)).thenReturn(null);
        PendingRegistrationRepository repository = new PendingRegistrationRepository(new RedisRepositoryFactory(
            template, new RedisConfig().redisTemplate(mock(RedisConnectionFactory.class))), template);

        assertTrue(repository.listActive().isEmpty());

        verify(zset).removeRangeByScore(eq(PendingRegistrationRepository.INDEX_KEY), eq(0.0), anyDouble());
        verify(zset, never()).remove(anyString(), (Object[]) org.mockito.ArgumentMatchers.any());
        verify(ops, never()).get(anyString());
    }
}
