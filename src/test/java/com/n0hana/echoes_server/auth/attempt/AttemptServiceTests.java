package com.n0hana.echoes_server.auth.attempt;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.n0hana.echoes_server.auth.exception.TooManyAttemptsException;
import com.n0hana.echoes_server.infra.redis.RedisRepository;
import com.n0hana.echoes_server.infra.redis.RedisRepositoryFactory;

@ExtendWith(MockitoExtension.class)
class AttemptServiceTests {

    @Mock
    private RedisRepositoryFactory factory;

    @Mock
    private RedisRepository<String> store;

    private AttemptService service;

    @BeforeEach
    void setUp() {
        when(factory.string(org.mockito.ArgumentMatchers.any())).thenReturn(store);
        service = new AttemptService(factory);
    }

    @Test
    @DisplayName("Abaixo do limite não bloqueia")
    void doesNotBlockBelowLimit() {
        when(store.count("a@b.com")).thenReturn(4L);

        assertDoesNotThrow(() -> service.assertNotBlocked(AttemptScope.MFA, "a@b.com"));
    }

    @Test
    @DisplayName("No limite bloqueia com 429")
    void blocksAtLimit() {
        when(store.count("a@b.com")).thenReturn(5L);

        assertThrows(TooManyAttemptsException.class,
                () -> service.assertNotBlocked(AttemptScope.MFA, "a@b.com"));
    }

    @Test
    @DisplayName("Pedido de reset bloqueia na terceira tentativa, não na quinta")
    void forgotHasLowerLimit() {
        when(store.count("a@b.com")).thenReturn(3L);

        assertThrows(TooManyAttemptsException.class,
                () -> service.assertNotBlocked(AttemptScope.PASSWORD_FORGOT, "a@b.com"));
    }

    @Test
    @DisplayName("register só devolve true quando o limite é atingido")
    void registerReportsExhaustion() {
        when(store.increment("a@b.com", AttemptScope.MFA.window())).thenReturn(4L, 5L);

        assertFalse(service.register(AttemptScope.MFA, "a@b.com"));
        assertTrue(service.register(AttemptScope.MFA, "a@b.com"));
    }

    @Test
    @DisplayName("E-mail é normalizado: maiúsculas e espaços usam o mesmo contador")
    void normalizesEmail() {
        when(store.increment("a@b.com", AttemptScope.LOGIN.window())).thenReturn(1L);

        service.register(AttemptScope.LOGIN, "  A@B.com ");

        verify(store).increment("a@b.com", AttemptScope.LOGIN.window());
    }

    @Test
    @DisplayName("clear apaga o contador")
    void clearDeletes() {
        service.clear(AttemptScope.LOGIN, "A@b.com");

        verify(store).delete("a@b.com");
    }
}
