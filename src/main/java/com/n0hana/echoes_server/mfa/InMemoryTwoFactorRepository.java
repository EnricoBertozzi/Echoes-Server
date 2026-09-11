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
        redisTemplate.opsForValue()
          .set(PREFIX + token.email(), token, 5, TimeUnit.MINUTES);
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
      Object cache = redisTemplate.opsForValue().get(email);
      return cache != null;
    }
}
