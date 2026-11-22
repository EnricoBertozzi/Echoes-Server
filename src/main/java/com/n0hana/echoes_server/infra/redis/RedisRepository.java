package com.n0hana.echoes_server.infra.redis;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import java.util.List;

import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

public class RedisRepository<T> {

    // GET + DEL num único passo no servidor: só remove se o valor for idêntico ao esperado
    private static final RedisScript<Long> CONSUME_IF_MATCHES = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end",
            Long.class);

    private static final long SCAN_BATCH = 200;

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

    /**
     * Ids (sem o prefixo do namespace) que começam com {@code idPrefix}.
     * Usa SCAN, que percorre o keyspace em lotes sem bloquear o Redis (ao contrário de KEYS).
     */
    public List<String> findIdsByPrefix(String idPrefix) {
        String base = namespace.getPrefix();
        ScanOptions options = ScanOptions.scanOptions()
                .match(escapeGlob(base + Objects.requireNonNull(idPrefix, "idPrefix")) + "*")
                .count(SCAN_BATCH)
                .build();

        List<String> ids = new ArrayList<>();
        try (Cursor<String> cursor = template.scan(options)) {
            cursor.forEachRemaining(key -> ids.add(key.substring(base.length())));
        }
        return ids;
    }

    /** Remove todas as chaves cujo id começa com {@code idPrefix}; devolve quantas foram removidas. */
    public long deleteByPrefix(String idPrefix) {
        List<String> keys = findIdsByPrefix(idPrefix).stream().map(this::key).toList();
        if (keys.isEmpty()) {
            return 0L;
        }
        Long removed = template.delete(keys);
        return removed == null ? 0L : removed;
    }

    /** escapa os curingas do padrão MATCH do Redis para que o prefixo seja literal */
    static String escapeGlob(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            if (c == '*' || c == '?' || c == '[' || c == ']' || c == '\\') {
                escaped.append('\\');
            }
            escaped.append(c);
        }
        return escaped.toString();
    }

    public RedisNamespace namespace() {
        return namespace;
    }

    private String key(String id) {
        return namespace.getPrefix() + Objects.requireNonNull(id, "id");
    }
}
