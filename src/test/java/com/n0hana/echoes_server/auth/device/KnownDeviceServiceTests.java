package com.n0hana.echoes_server.auth.device;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.n0hana.echoes_server.infra.redis.RedisRepository;
import com.n0hana.echoes_server.infra.redis.RedisRepositoryFactory;

class KnownDeviceServiceTests {

    private final UUID userId = UUID.randomUUID();

    /** repositórios em memória: exists/save reais, sem Redis */
    private RedisRepository<String> memoryRepo() {
        java.util.Map<String, String> store = new java.util.HashMap<>();
        RedisRepository<String> repo = mock(RedisRepository.class);
        when(repo.exists(anyString())).thenAnswer(inv -> store.containsKey(inv.getArgument(0)));
        org.mockito.Mockito.doAnswer(inv -> { store.put(inv.getArgument(0), inv.getArgument(1)); return null; })
                .when(repo).save(anyString(), anyString());
        return repo;
    }

    private KnownDeviceService service() {
        KnownDeviceService s = new KnownDeviceService(mock(RedisRepositoryFactory.class));
        ReflectionTestUtils.setField(s, "devices", memoryRepo());
        ReflectionTestUtils.setField(s, "users", memoryRepo());
        return s;
    }

    @Test
    @DisplayName("Primeiro login do usuário não dispara alerta (RF11)")
    void firstLoginDoesNotAlert() {
        assertFalse(service().registerAndCheckNew(userId, "Chrome/120"));
    }

    @Test
    @DisplayName("Navegador novo depois de um conhecido dispara alerta (RF11)")
    void newBrowserAlertsAfterKnownOne() {
        KnownDeviceService s = service();
        s.registerAndCheckNew(userId, "Chrome/120");

        assertTrue(s.registerAndCheckNew(userId, "Firefox/130"));
    }

    @Test
    @DisplayName("Navegador já conhecido não alerta de novo")
    void knownBrowserDoesNotAlert() {
        KnownDeviceService s = service();
        s.registerAndCheckNew(userId, "Chrome/120");
        s.registerAndCheckNew(userId, "Firefox/130");

        assertFalse(s.registerAndCheckNew(userId, "Chrome/120"));
    }
}
