package com.n0hana.echoes_server.auth;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.n0hana.echoes_server.infra.redis.CodeType;
import com.n0hana.echoes_server.infra.redis.RedisRepository;
import com.n0hana.echoes_server.infra.redis.RedisRepositoryFactory;

@Repository
public class AuthRepository {

    private final RedisRepository<String> store;

    public AuthRepository(RedisRepositoryFactory factory) {
        this.store = factory.string(CodeType.MFA);
    }

    public void save(String email, String code) {
        store.save(email, code);
    }

    public Optional<String> findByEmail(String email) {
        return store.find(email);
    }

    public void delete(String email) {
        store.delete(email);
    }
}
