package com.n0hana.echoes_server.repository.memory;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

import com.n0hana.echoes_server.dto.TwoFactorDto;

@Repository
public class InMemoryTwoFactorRepository {

    private final Map<String, TwoFactorDto> storage = new ConcurrentHashMap<>();

    public TwoFactorDto save(TwoFactorDto token) {
        storage.put(token.email(), token);
        return token;
    }

    public Optional<TwoFactorDto> findByEmail(String email) {
        TwoFactorDto token = storage.get(email);

        if (token == null) {
            return Optional.empty();
        }

        // auto-expire logic
        if (token.expiresAt().isBefore(Instant.now())) {
            storage.remove(email);
            return Optional.empty();
        }

        return Optional.of(token);
    }

    public void deleteByEmail(String email) {
        storage.remove(email);
    }

    public boolean existsByEmail(String email) {
        return storage.containsKey(email);
    }
}