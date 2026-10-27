package com.n0hana.echoes_server.infra.redis;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.n0hana.echoes_server.infra.RedisConfig;
import com.n0hana.echoes_server.user.PendingRegistration;

class RedisRepositoryFactoryTest {

    private StringRedisTemplate stringTemplate;
    private ValueOperations<String, String> ops;
    private RedisRepositoryFactory factory;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        stringTemplate = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        when(stringTemplate.opsForValue()).thenReturn(ops);
        RedisTemplate<String, Object> objectTemplate =
                new RedisConfig().redisTemplate(mock(RedisConnectionFactory.class));
        factory = new RedisRepositoryFactory(stringTemplate, objectTemplate);
    }

    private static RedisNamespace ns(String prefix) {
        return new RedisNamespace() {
            @Override public String getPrefix() { return prefix; }
            @Override public Duration getDuration() { return Duration.ofMinutes(1); }
        };
    }

    @Test
    @DisplayName("todos os namespaces do projeto têm prefixos únicos e sem sobreposição")
    void allProjectNamespacesAreCompatible() {
        assertDoesNotThrow(() -> {
            for (CodeType t : CodeType.values()) factory.string(t);
            for (ObjectType t : ObjectType.values()) factory.typed(t, Object.class);
        });
    }

    @Test
    @DisplayName("mesmo namespace e mesmo tipo de valor reaproveita o repositório (string)")
    void reusesStringRepositoryForSameNamespace() {
        RedisRepository<String> first = factory.string(CodeType.MFA);
        RedisRepository<String> second = factory.string(CodeType.MFA);

        assertSame(first, second);
    }

    @Test
    @DisplayName("mesmo namespace e mesma classe reaproveita o repositório (tipado)")
    void reusesTypedRepositoryForSameNamespace() {
        RedisRepository<PendingRegistration> first =
                factory.typed(ObjectType.PENDING_REGISTRATION, PendingRegistration.class);
        RedisRepository<PendingRegistration> second =
                factory.typed(ObjectType.PENDING_REGISTRATION, PendingRegistration.class);

        assertSame(first, second);
    }

    @Test
    @DisplayName("namespaces diferentes geram repositórios diferentes")
    void differentNamespacesGetDifferentRepositories() {
        assertNotSame(factory.string(CodeType.MFA), factory.string(CodeType.PASSWORD_RESET));
    }

    @Test
    @DisplayName("mesmo namespace com tipo de valor diferente é rejeitado")
    void rejectsSameNamespaceWithDifferentKind() {
        factory.string(CodeType.MFA);

        assertThrows(IllegalStateException.class, () -> factory.typed(CodeType.MFA, PendingRegistration.class));
    }

    @Test
    @DisplayName("prefixo duplicado em outro namespace é rejeitado")
    void rejectsDuplicatePrefix() {
        factory.string(ns("a:"));
        assertThrows(IllegalStateException.class, () -> factory.string(ns("a:")));
    }

    @Test
    @DisplayName("prefixo contido no início de outro é rejeitado, nos dois sentidos")
    void rejectsOverlappingPrefix() {
        factory.string(ns("password:code:"));
        assertThrows(IllegalStateException.class, () -> factory.string(ns("password:")));
        assertThrows(IllegalStateException.class, () -> factory.string(ns("password:code:extra:")));
    }

    @Test
    @DisplayName("prefixo vazio e TTL inválido são rejeitados")
    void rejectsInvalidNamespace() {
        assertThrows(IllegalArgumentException.class, () -> factory.string(ns(" ")));
        assertThrows(IllegalArgumentException.class, () -> factory.string(new RedisNamespace() {
            @Override public String getPrefix() { return "zero:"; }
            @Override public Duration getDuration() { return Duration.ZERO; }
        }));
    }

    @Test
    @DisplayName("repositório tipado faz round-trip de PendingRegistration com o formato do RedisConfig")
    void typedRepositoryRoundTrip() {
        RedisRepository<PendingRegistration> repo =
                factory.typed(ObjectType.PENDING_REGISTRATION, PendingRegistration.class);
        PendingRegistration original = new PendingRegistration(
                "João", "joao@example.com", "STUDENT", UUID.randomUUID(),
                "123456", Instant.now().plusSeconds(300), 3, Instant.now());

        repo.save(original.email(), original);

        ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);
        verify(ops).set(eq("pending:registration:joao@example.com"), stored.capture(), eq(Duration.ofMinutes(15)));
        when(ops.get("pending:registration:joao@example.com")).thenReturn(stored.getValue());

        PendingRegistration restored = repo.find(original.email()).orElseThrow();
        assertEquals(original, restored);
    }
}
