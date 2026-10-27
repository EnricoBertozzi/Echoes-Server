package com.n0hana.echoes_server.infra.security;

import java.time.Duration;

import org.springframework.stereotype.Repository;

import com.n0hana.echoes_server.infra.redis.CodeType;
import com.n0hana.echoes_server.infra.redis.RedisRepository;
import com.n0hana.echoes_server.infra.redis.RedisRepositoryFactory;

@Repository
public class JwtBlackListRepository {

    private static final String REVOKED = "revoked";

    private final RedisRepository<String> store;

    public JwtBlackListRepository(RedisRepositoryFactory factory) {
        this.store = factory.string(CodeType.JWT_BLACKLIST);
    }

    public void save(String jti, Duration ttl) {
        store.save(jti, REVOKED, ttl);
    }

    public boolean exists(String jti) {
        return store.exists(jti);
    }
}
