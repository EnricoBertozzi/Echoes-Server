package com.n0hana.echoes_server.infra.redis;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

public class RedisRepository<T> {

    // GET + DEL num único passo no servidor: só remove se o valor for idêntico ao esperado
    private static final RedisScript<Long> CONSUME_IF_MATCHES = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate template;
    private final RedisNamespace namespace;
    private final ValueCodec<T> codec;

    RedisRepository(StringRedisTemplate template, RedisNamespace namespace, ValueCodec<T> codec) {
        this.template = Objects.requireNonNull(template, "template");
        this.namespace = Objects.requireNonNull(namespace, "namespace");
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    public void save(String id, T value) {
        save(id, value, namespace.getDuration());
    }

    public void save(String id, T value, Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("TTL deve ser positivo");
        }
        template.opsForValue().set(key(id), codec.encode(value), ttl);
    }

    public Optional<T> find(String id) {
        return Optional.ofNullable(template.opsForValue().get(key(id))).map(codec::decode);
    }

    /** soma 1 ao contador; o ttl só começa na primeira soma, então a janela não se renova */
    public long increment(String id, Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("TTL deve ser positivo");
        }
        Long value = template.opsForValue().increment(key(id));
        if (value == null) {
            return 0L;
        }
        if (value == 1L) {
            template.expire(key(id), ttl);
        }
        return value;
    }

    public long count(String id) {
        String raw = template.opsForValue().get(key(id));
        return raw == null ? 0L : Long.parseLong(raw);
    }

    public boolean exists(String id) {
        return Boolean.TRUE.equals(template.hasKey(key(id)));
    }

    public void delete(String id) {
        template.delete(key(id));
    }

    /**
     * Remove a chave atomicamente apenas se o valor armazenado for igual ao esperado.
     * Valor diferente ou chave ausente: nada é removido e retorna false.
     */
    public boolean consumeIfMatches(String id, T expected) {
        Long removed = template.execute(CONSUME_IF_MATCHES, List.of(key(id)), codec.encode(expected));
        return removed != null && removed == 1L;
    }

    public Optional<Duration> remainingTtl(String id) {
        Long millis = template.getExpire(key(id), TimeUnit.MILLISECONDS);
        return (millis == null || millis < 0) ? Optional.empty() : Optional.of(Duration.ofMillis(millis));
    }

    public RedisNamespace namespace() {
        return namespace;
    }

    private String key(String id) {
        return namespace.getPrefix() + Objects.requireNonNull(id, "id");
    }
}
