package com.n0hana.echoes_server.auth.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.n0hana.echoes_server.auth.device.KnownDeviceService.KnownDevice;
import com.n0hana.echoes_server.infra.redis.RedisRepository;
import com.n0hana.echoes_server.infra.redis.RedisRepositoryFactory;

class KnownDeviceServiceTests {

    private final UUID userId = UUID.randomUUID();

    private final Map<String, String> deviceStore = new HashMap<>();
    private final Map<String, String> userStore = new HashMap<>();

    /** repositório em memória: exists/save/find/delete e busca por prefixo reais, sem Redis */
    @SuppressWarnings("unchecked")
    private RedisRepository<String> memoryRepo(Map<String, String> store) {
        RedisRepository<String> repo = mock(RedisRepository.class);
        when(repo.exists(anyString())).thenAnswer(inv -> store.containsKey(inv.getArgument(0)));
        when(repo.find(anyString())).thenAnswer(inv -> Optional.ofNullable(store.get(inv.getArgument(0))));
        when(repo.remainingTtl(anyString())).thenAnswer(inv ->
                store.containsKey(inv.getArgument(0)) ? Optional.of(Duration.ofDays(180)) : Optional.empty());
        when(repo.findIdsByPrefix(anyString())).thenAnswer(inv -> store.keySet().stream()
                .filter(k -> k.startsWith(inv.getArgument(0)))
                .sorted()
                .toList());
        when(repo.deleteByPrefix(anyString())).thenAnswer(inv -> {
            String prefix = inv.getArgument(0);
            List<String> keys = store.keySet().stream().filter(k -> k.startsWith(prefix)).toList();
            keys.forEach(store::remove);
            return (long) keys.size();
        });
        doAnswer(inv -> store.put(inv.getArgument(0), inv.getArgument(1)))
                .when(repo).save(anyString(), anyString());
        doAnswer(inv -> store.remove(inv.getArgument(0))).when(repo).delete(anyString());
        return repo;
    }

    private KnownDeviceService service() {
        KnownDeviceService s = new KnownDeviceService(mock(RedisRepositoryFactory.class));
        ReflectionTestUtils.setField(s, "devices", memoryRepo(deviceStore));
        ReflectionTestUtils.setField(s, "users", memoryRepo(userStore));
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

    @Test
    @DisplayName("Lista os navegadores do titular com hash, primeiro uso e expiração")
    void listsOnlyOwnDevices() {
        KnownDeviceService s = service();
        s.registerAndCheckNew(userId, "Chrome/120");
        s.registerAndCheckNew(userId, "Firefox/130");
        s.registerAndCheckNew(UUID.randomUUID(), "Safari/17");

        List<KnownDevice> devices = s.list(userId);

        assertEquals(2, devices.size());
        assertTrue(devices.stream().map(KnownDevice::fingerprint)
                .anyMatch(KnownDeviceService.fingerprint("Chrome/120")::equals));
        devices.forEach(d -> {
            assertEquals(64, d.fingerprint().length(), "SHA-256 em hex, sem o User-Agent em claro");
            assertNotNull(d.firstSeenAt());
            assertTrue(d.expiresAt().isAfter(Instant.now().plus(Duration.ofDays(179))));
        });
    }

    @Test
    @DisplayName("Registro antigo (valor \"1\") aparece sem data de primeiro uso")
    void legacyEntryHasNoFirstSeen() {
        KnownDeviceService s = service();
        deviceStore.put(userId + ":" + KnownDeviceService.fingerprint("Chrome/120"), "1");

        KnownDevice device = s.list(userId).getFirst();

        assertNull(device.firstSeenAt());
        assertNotNull(device.expiresAt());
    }

    @Test
    @DisplayName("forget apaga todos os navegadores do titular e a marca do usuário, sem tocar em outros")
    void forgetRemovesOnlyOwnKeys() {
        KnownDeviceService s = service();
        UUID other = UUID.randomUUID();
        s.registerAndCheckNew(userId, "Chrome/120");
        s.registerAndCheckNew(userId, "Firefox/130");
        s.registerAndCheckNew(other, "Safari/17");

        assertEquals(2L, s.forget(userId));

        assertTrue(s.list(userId).isEmpty());
        assertFalse(userStore.containsKey(userId.toString()));
        assertEquals(1, s.list(other).size());
        assertTrue(userStore.containsKey(other.toString()));
        // depois de esquecido, o próximo login volta a ser o "primeiro" e não alerta
        assertFalse(s.registerAndCheckNew(userId, "Chrome/120"));
    }
}
