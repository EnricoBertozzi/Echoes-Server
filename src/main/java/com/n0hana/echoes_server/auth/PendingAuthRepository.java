package com.n0hana.echoes_server.auth;

import com.n0hana.echoes_server.auth.dto.AuthRequestDTO;

import lombok.RequiredArgsConstructor;

import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PendingAuthRepository {

    private final RedisTemplate<String, Object> redisTemplate;
    private final String PREFIX = "auth:";

    public void save(AuthRequestDTO dto) {
      redisTemplate.opsForValue()
        .set(PREFIX + dto.email(), dto, 5, TimeUnit.MINUTES);
    }

    public AuthRequestDTO find(String email) {
      String key = PREFIX + email;
      Object cache = redisTemplate.opsForValue().get(key);
      return (AuthRequestDTO) cache;
    }

    public void delete(String email) {
      String key = PREFIX + email;
      redisTemplate.opsForValue().getAndDelete(key);
    }
}
