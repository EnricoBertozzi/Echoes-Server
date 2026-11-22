package com.n0hana.echoes_server.auth.device;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
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
 *
 * <p>
 * Chaves: {@code known-device:<userId>:<impressão>} (valor = instante do primeiro
 * uso) e {@code known-device-user:<userId>} (marca que o usuário já tem navegadores
 * conhecidos). Ambas expiram em 180 dias e são apagadas na exclusão da conta.
 * </p>
 */
@Service
public class KnownDeviceService {

    /** valor gravado antes de guardarmos o instante do primeiro uso */
    private static final String LEGACY_VALUE = "1";

    private final RedisRepository<String> devices;
    private final RedisRepository<String> users;

    public KnownDeviceService(RedisRepositoryFactory factory) {
        this.devices = factory.string(CodeType.KNOWN_DEVICE);
        this.users = factory.string(CodeType.KNOWN_DEVICE_USER);
    }

    /** navegador conhecido do titular, como aparece na exportação dos dados (RF18) */
    public record KnownDevice(String fingerprint, Instant firstSeenAt, Instant expiresAt) {
    }

    /**
     * Registra o dispositivo do login atual.
     *
     * @return {@code true} se é um dispositivo novo para um usuário que já tinha
     *         dispositivos conhecidos (dispara o alerta); o primeiro login não alerta
     */
    public boolean registerAndCheckNew(UUID userId, String userAgent) {
        String key = deviceKey(userId, fingerprint(userAgent));

        if (devices.exists(key)) {
            return false;
        }

        boolean hadDevices = users.exists(userId.toString());

        devices.save(key, Instant.now().toString());
        users.save(userId.toString(), LEGACY_VALUE);

        return hadDevices;
    }

    /** navegadores conhecidos do titular, do primeiro uso mais recente para o mais antigo */
    public List<KnownDevice> list(UUID userId) {
        String prefix = userPrefix(userId);
        Instant now = Instant.now();

        return devices.findIdsByPrefix(prefix).stream()
                .map(id -> new KnownDevice(
                        id.substring(prefix.length()),
                        devices.find(id).map(KnownDeviceService::parseInstant).orElse(null),
                        devices.remainingTtl(id).map(now::plus).orElse(null)))
                .sorted(Comparator.comparing(KnownDevice::firstSeenAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    /**
     * Apaga todos os navegadores conhecidos do titular (exclusão da conta, LGPD art. 18, VI).
     *
     * @return quantas impressões de navegador foram removidas
     */
    public long forget(UUID userId) {
        long removed = devices.deleteByPrefix(userPrefix(userId));
        users.delete(userId.toString());
        return removed;
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

    private static String userPrefix(UUID userId) {
        return userId + ":";
    }

    private static String deviceKey(UUID userId, String fingerprint) {
        return userPrefix(userId) + fingerprint;
    }

    /** registros antigos guardam "1": o instante do primeiro uso é desconhecido */
    private static Instant parseInstant(String value) {
        if (value == null || LEGACY_VALUE.equals(value)) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
