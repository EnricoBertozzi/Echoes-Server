package com.n0hana.echoes_server.service;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.n0hana.echoes_server.service.auth.JwtTokenService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BlackListService {
    
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
