package com.n0hana.echoes_server.repository.memory;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.n0hana.echoes_server.dto.PendingRegisterDTO;

import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Repository;

@Repository
public class PendingRegisterRepository {

    private final Cache<String, PendingRegisterDTO> cache =
        Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(1000)
                .build();

    public void save(PendingRegisterDTO dto) {
        cache.put(dto.email(), dto);
    }

    public PendingRegisterDTO find(String email) {
        return cache.getIfPresent(email);
    }

    public void delete(String email) {
        cache.invalidate(email);
    }
}
