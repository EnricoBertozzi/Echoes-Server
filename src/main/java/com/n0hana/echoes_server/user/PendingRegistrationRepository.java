package com.n0hana.echoes_server.user;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.n0hana.echoes_server.infra.redis.ObjectType;
import com.n0hana.echoes_server.infra.redis.RedisRepository;
import com.n0hana.echoes_server.infra.redis.RedisRepositoryFactory;

@Repository
public class PendingRegistrationRepository {

    private static final Duration RETENTION = ObjectType.PENDING_REGISTRATION.getDuration();

    private final RedisRepository<PendingRegistration> store;

    public PendingRegistrationRepository(RedisRepositoryFactory factory) {
        this.store = factory.typed(ObjectType.PENDING_REGISTRATION, PendingRegistration.class);
    }

    public PendingRegistration save(PendingRegistration pending) {
        store.save(pending.email(), pending);
        return pending;
    }

    public void savePreservingTtl(PendingRegistration pending) {
        Duration remaining = Duration.between(Instant.now(), pending.createdAt().plus(RETENTION));
        if (!remaining.isNegative() && !remaining.isZero()) {
            store.save(pending.email(), pending, remaining);
        }
    }

    public Optional<PendingRegistration> findByEmail(String email) {
        return store.find(email);
    }

    public void deleteByEmail(String email) {
        store.delete(email);
    }
}
