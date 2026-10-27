package com.n0hana.echoes_server.infra.redis;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Component;

@Component
public class RedisRepositoryFactory {

    private static final Object STRING_KIND = new Object();

    private record Registration(RedisNamespace namespace, Object kind, RedisRepository<?> repository) {
    }

    private final StringRedisTemplate template;
    private final RedisSerializer<Object> typedSerializer;
    private final List<Registration> registered = new ArrayList<>();

    @SuppressWarnings("unchecked")
    public RedisRepositoryFactory(StringRedisTemplate template, RedisTemplate<String, Object> objectTemplate) {
        this.template = template;
        this.typedSerializer = (RedisSerializer<Object>) objectTemplate.getValueSerializer();
    }

    public RedisRepository<String> string(RedisNamespace namespace) {
        return reuseOrRegister(namespace, STRING_KIND, ValueCodec::string);
    }

    public <T> RedisRepository<T> typed(RedisNamespace namespace, Class<T> type) {
        Objects.requireNonNull(type, "type");
        return reuseOrRegister(namespace, type, () -> ValueCodec.typed(typedSerializer, type));
    }

    /** Codec customizado: nunca é reaproveitado, sempre registra um novo namespace. */
    public synchronized <T> RedisRepository<T> create(RedisNamespace namespace, ValueCodec<T> codec) {
        return register(namespace, new Object(), codec);
    }

    @SuppressWarnings("unchecked")
    private synchronized <T> RedisRepository<T> reuseOrRegister(
            RedisNamespace namespace, Object kind, Supplier<ValueCodec<T>> codec) {
        Objects.requireNonNull(namespace, "namespace");
        for (Registration r : registered) {
            if (r.namespace().equals(namespace) && r.kind().equals(kind)) {
                return (RedisRepository<T>) r.repository();
            }
        }
        return register(namespace, kind, codec.get());
    }

    private <T> RedisRepository<T> register(RedisNamespace namespace, Object kind, ValueCodec<T> codec) {
        validate(namespace);
        RedisRepository<T> repository = new RedisRepository<>(template, namespace, codec);
        registered.add(new Registration(namespace, kind, repository));
        return repository;
    }

    private void validate(RedisNamespace namespace) {
        Objects.requireNonNull(namespace, "namespace");
        String prefix = namespace.getPrefix();
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("Prefixo Redis não pode ser vazio");
        }
        Duration ttl = namespace.getDuration();
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("TTL padrão inválido para o prefixo '" + prefix + "'");
        }
        for (Registration r : registered) {
            String p = r.namespace().getPrefix();
            if (p.startsWith(prefix) || prefix.startsWith(p)) {
                throw new IllegalStateException(
                        "Prefixo Redis '" + prefix + "' conflita com '" + p + "' (duplicado ou sobreposto)");
            }
        }
    }
}
