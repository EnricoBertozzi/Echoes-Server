package com.n0hana.echoes_server.infra.redis;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

@Component
public class RedisCodeStore {

    private final Map<CodeType, RedisRepository<String>> repositories = new EnumMap<>(CodeType.class);

    public RedisCodeStore(RedisRepositoryFactory factory) {
        for (CodeType type : CodeType.values()) {
            repositories.put(type, factory.string(type));
        }
    }

    public void save(String identify, String value, CodeType type) {
        repo(type).save(identify, value);
    }

    public void save(String identify, String value, CodeType type, Duration customTtl) {
        repo(type).save(identify, value, customTtl);
    }

    public boolean isExists(String identify, CodeType type) {
        return repo(type).exists(identify);
    }

    public Optional<String> get(String identify, CodeType type) {
        return repo(type).find(identify);
    }

    public void delete(String identify, CodeType type) {
        repo(type).delete(identify);
    }

    public Long getExpire(String identify, CodeType type) {
        return repo(type).remainingTtl(identify).map(Duration::toSeconds).orElse(-1L);
    }

    private RedisRepository<String> repo(CodeType type) {
        return repositories.get(type);
    }
}
