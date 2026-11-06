package com.n0hana.echoes_server.infra.redis;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;

public class RedisRepository<T> {

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
