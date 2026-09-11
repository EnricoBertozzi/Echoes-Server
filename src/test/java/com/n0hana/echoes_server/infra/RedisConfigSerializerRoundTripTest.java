package com.n0hana.echoes_server.infra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import com.n0hana.echoes_server.user.PendingRegistration;

/**
 * Trava a configuração do serializer do Redis: sem default typing no mapper,
 * a desserialização devolve LinkedHashMap e o cast do repositório explode em
 * tempo de execução (bug que nenhum teste com mock pegaria).
 */
class RedisConfigSerializerRoundTripTest {

    @Test
    @DisplayName("PendingRegistration faz round-trip pelo serializer real do RedisConfig com todos os campos intactos")
    void pendingRegistrationRoundTripsWithTypeInfo() {
        RedisTemplate<String, Object> template =
                new RedisConfig().redisTemplate(mock(RedisConnectionFactory.class));
        GenericJackson2JsonRedisSerializer serializer =
                (GenericJackson2JsonRedisSerializer) template.getValueSerializer();

        PendingRegistration original = new PendingRegistration(
                "João", "joao@example.com", "STUDENT", UUID.randomUUID(),
                "123456", Instant.now().plusSeconds(300), 3, Instant.now());

        byte[] json = serializer.serialize(original);
        Object deserialized = serializer.deserialize(json);

        PendingRegistration restored = assertInstanceOf(PendingRegistration.class, deserialized);
        assertEquals(original.name(), restored.name());
        assertEquals(original.email(), restored.email());
        assertEquals(original.role(), restored.role());
        assertEquals(original.institutionId(), restored.institutionId());
        assertEquals(original.code(), restored.code());
        assertEquals(original.expiresAt(), restored.expiresAt());
        assertEquals(original.attempts(), restored.attempts());
        assertEquals(original.createdAt(), restored.createdAt());
    }
}
