package com.n0hana.echoes_server.auth.password;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.n0hana.echoes_server.infra.redis.CodeType;
import com.n0hana.echoes_server.infra.redis.RedisRepository;
import com.n0hana.echoes_server.infra.redis.RedisRepositoryFactory;

@Repository
public class PasswordTokenRepository {

    private final RedisRepository<String> store;

    public PasswordTokenRepository(RedisRepositoryFactory factory) {
        this.store = factory.string(CodeType.PASSWORD_TOKEN);
    }

    public void save(String email, String token) {
        store.save(email, token);
    }

    public Optional<String> findByEmail(String email) {
        return store.find(email);
    }

    public void delete(String email) {
        store.delete(email);
    }
}
