package com.n0hana.echoes_server.infra.redis;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.springframework.data.redis.serializer.RedisSerializer;

public interface ValueCodec<T> {

    String encode(T value);

    T decode(String raw);

    static ValueCodec<String> string() {
        return new ValueCodec<>() {
            @Override
            public String encode(String value) {
                return Objects.requireNonNull(value, "value");
            }

            @Override
            public String decode(String raw) {
                return raw;
            }
        };
    }

    static <T> ValueCodec<T> typed(RedisSerializer<Object> serializer, Class<T> type) {
        Objects.requireNonNull(serializer, "serializer");
        Objects.requireNonNull(type, "type");
        return new ValueCodec<>() {
            @Override
            public String encode(T value) {
                byte[] bytes = serializer.serialize(Objects.requireNonNull(value, "value"));
                return new String(bytes, StandardCharsets.UTF_8);
            }

            @Override
            public T decode(String raw) {
                return type.cast(serializer.deserialize(raw.getBytes(StandardCharsets.UTF_8)));
            }
        };
    }
}
