package com.n0hana.echoes_server.user;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class PendingRegistrationRepository {

    private static final String PREFIX = "pending:registration:";
    private static final Duration RETENTION = Duration.ofMinutes(15);

    private final RedisTemplate<String, Object> redisTemplate;

    public PendingRegistration save(PendingRegistration pending) {
        // TTL de retenção (15 min) é maior que a validade do código (5 min,
        // definida pelo chamador via expiresAt): convites expirados continuam
        // recuperáveis para que a verificação distinga "expirado" de
        // "inexistente" em vez de o Redis evadir silenciosamente a entrada.
        redisTemplate.opsForValue().set(key(pending.email()), pending, RETENTION);
        return pending;
    }

    /**
     * Re-salva após incremento de tentativas erradas preservando o TTL
     * original do convite (não estende a janela de força bruta).
     */
    public void savePreservingTtl(PendingRegistration pending) {
        Duration remaining = Duration.between(Instant.now(), pending.createdAt().plus(RETENTION));
        if (!remaining.isNegative() && !remaining.isZero()) {
            redisTemplate.opsForValue().set(key(pending.email()), pending, remaining);
        }
    }

    public Optional<PendingRegistration> findByEmail(String email) {
        return Optional.ofNullable((PendingRegistration) redisTemplate.opsForValue().get(key(email)));
    }

    public void deleteByEmail(String email) {
        redisTemplate.opsForValue().getAndDelete(key(email));
    }

    private String key(String email) {
        return PREFIX + email;
    }
}
