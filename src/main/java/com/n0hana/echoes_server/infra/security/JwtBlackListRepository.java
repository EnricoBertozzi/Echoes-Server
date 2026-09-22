package com.n0hana.echoes_server.infra.security;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * Repositório para armazenamento de tokens revogados pelos usuários
 * 
 * @author Enrico Bertozzi
 * @since 0.1.1
 */
@Repository
public class JwtBlackListRepository {

    private final String PREFIX = "blacklist:token:";

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    /**
     * Salva um identificador de token revogado em um banco em memória
     * 
     * @param jti Identificador do token
     * @param ttl Tempo de expiração do registro
     */
    public void save(String jti, Duration ttl) {
        redisTemplate.opsForValue().set(key(jti), "revoked");
    }

    /**
     * Verifica se um token está revogado
     * 
     * @param jti Identificador do token
     * @return {@link boolean} referente se o token está revogado ou não
     */
    public boolean exists(String jti) {
        return redisTemplate.hasKey(key(jti));
    }

    /**
     * Gera o prefixo da chave para salvamento no redis
     * 
     * @param jti Identificador do token
     * 
     * @return {@link String} contendo a chave formatada.
     */
    private String key(String jti) {
        return PREFIX + jti;
    }
}
