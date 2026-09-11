package com.n0hana.echoes_server.mfa;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class InMemoryTwoFactorRepository {

    private final RedisTemplate<String, Object> redisTemplate;
    private final String PREFIX = "twofactor:";

    public TwoFactorDTO save(TwoFactorDTO token) {
        // TTL de retenção (15 min) é maior que a validade do código (5 min,
        // definida pelo chamador via expiresAt): códigos expirados continuam
        // recuperáveis para que a verificação distinga "expirado" de
        // "inexistente" em vez de o Redis evadir silenciosamente a entrada.
        redisTemplate.opsForValue()
          .set(PREFIX + token.email(), token, 15, TimeUnit.MINUTES);
        return token;
    }

    public Optional<TwoFactorDTO> findByEmail(String email) {
      String key = PREFIX + email;
      Object cache = redisTemplate.opsForValue().get(key);
      return Optional.ofNullable((TwoFactorDTO)cache);
    }

    public void deleteByEmail(String email) {
      String key = PREFIX + email;
      redisTemplate.opsForValue().getAndDelete(key);
    }

    public boolean existsByEmail(String email) {
      Object cache = redisTemplate.opsForValue().get(PREFIX + email);
      return cache != null;
    }
}
