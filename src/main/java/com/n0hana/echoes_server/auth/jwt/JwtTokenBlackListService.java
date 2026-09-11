package com.n0hana.echoes_server.auth.jwt;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class JwtTokenBlackListService {
    
    private final JwtTokenService tokenService;
    private final StringRedisTemplate redis;
    private final String PREFIX = "blacklist:";

    public void revokeToken(String token, Duration duration) {
        String jti = tokenService.extractJti(token);

        redis.opsForValue()
            .set(PREFIX + jti, "revoked", duration);
    }

    public boolean isRevokedToken(String token) {
        String jti = tokenService.extractJti(token);
        return redis.hasKey(PREFIX + jti);
    }

}
