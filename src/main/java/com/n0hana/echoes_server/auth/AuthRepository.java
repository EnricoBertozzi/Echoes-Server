package com.n0hana.echoes_server.auth;

import java.time.Duration;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuthRepository {

    private final String PREFIX = "pending:login:";
    private final Duration TTL = Duration.ofMinutes(15);

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    public void save(String email, String code) {
        redisTemplate.opsForValue().set(key(email), code, TTL);
    }

    public Optional<String> findByEmail(String email) {
        return Optional
                .ofNullable(redisTemplate.opsForValue().get(key(email)));
    }

    public void delete(String email) {
        redisTemplate.opsForValue().getAndDelete(key(email));
    }

    private String key(String email) {
        return PREFIX + email;
    }

}
