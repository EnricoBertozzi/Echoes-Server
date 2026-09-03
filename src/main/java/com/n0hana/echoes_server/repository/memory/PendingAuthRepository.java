package com.n0hana.echoes_server.repository.memory;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.n0hana.echoes_server.dto.AuthRequestDTO;

import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Repository;

@Repository
public class PendingAuthRepository {

    private final Cache<String, AuthRequestDTO> cache =
        Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(1000)
                .build();

    public void save(AuthRequestDTO dto) {
        cache.put(dto.email(), dto);
    }

    public AuthRequestDTO find(String email) {
        return cache.getIfPresent(email);
    }

    public void delete(String email) {
        cache.invalidate(email);
    }
}
