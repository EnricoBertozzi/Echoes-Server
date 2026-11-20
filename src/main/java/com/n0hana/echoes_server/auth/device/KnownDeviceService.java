package com.n0hana.echoes_server.auth.device;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.n0hana.echoes_server.infra.redis.CodeType;
import com.n0hana.echoes_server.infra.redis.RedisRepository;
import com.n0hana.echoes_server.infra.redis.RedisRepositoryFactory;

/**
 * Guarda a impressão de cada navegador que o titular já usou (RF11).
 *
 * <p>
 * A impressão é o hash do User-Agent. Não usamos o IP: ele muda com frequência
 * (rede móvel, Wi-Fi), o que geraria alertas falsos.
 * </p>
 */
@Service
public class KnownDeviceService {

    private final RedisRepository<String> devices;
    private final RedisRepository<String> users;

    public KnownDeviceService(RedisRepositoryFactory factory) {
        this.devices = factory.string(CodeType.KNOWN_DEVICE);
        this.users = factory.string(CodeType.KNOWN_DEVICE_USER);
    }

    /**
     * Registra o dispositivo do login atual.
     *
     * @return {@code true} se é um dispositivo novo para um usuário que já tinha
     *         dispositivos conhecidos (dispara o alerta); o primeiro login não alerta
     */
    public boolean registerAndCheckNew(UUID userId, String userAgent) {
        String key = userId + ":" + fingerprint(userAgent);

        if (devices.exists(key)) {
            return false;
        }

        boolean hadDevices = users.exists(userId.toString());

        devices.save(key, "1");
        users.save(userId.toString(), "1");

        return hadDevices;
    }

    static String fingerprint(String userAgent) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((userAgent == null ? "" : userAgent).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
