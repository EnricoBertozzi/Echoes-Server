package com.n0hana.echoes_server.device;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.n0hana.echoes_server.infra.redis.CodeType;
import com.n0hana.echoes_server.infra.redis.RedisRepository;
import com.n0hana.echoes_server.infra.redis.RedisRepositoryFactory;

/** Guarda, por dispositivo, o instante (em segundos) a partir do qual os tokens anteriores são inválidos. */
@Repository
public class DeviceTokenRevocationRepository {

    private final RedisRepository<String> store;

    public DeviceTokenRevocationRepository(RedisRepositoryFactory factory) {
        this.store = factory.string(CodeType.DEVICE_TOKENS_REVOKED);
    }

    public void revokeUntil(UUID deviceId, long epochSecond) {
        store.save(deviceId.toString(), Long.toString(epochSecond));
    }

    public Optional<Long> findCutoff(UUID deviceId) {
        return store.find(deviceId.toString()).map(Long::valueOf);
    }
}
