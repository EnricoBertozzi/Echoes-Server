package com.n0hana.echoes_server.auth.password;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PasswordTokenRepository {
    // TODO criar implementação genérica de repositórios em memoria

    private final String PREFIX = "password:token:";

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    /**
     * Salva o token de redefinição de senha de um usuário
     * 
     * @param email Email do usuário
     * @param token  Token de redefinição
     */
    public void save(String email, String token) {
        redisTemplate.opsForValue().set(key(email), token);
    }

    /**
     * Busca um token armazenado no banco em memória
     * 
     * @param email Email do usuário
     * 
     * @return {@link Optional} com o token de redefinição de senha
     */
    public Optional<String> findByEmail(String email) {
        return Optional
                .ofNullable(redisTemplate.opsForValue().get(key(email)));
    }

    /**
     * Remove um token armazenado no banco em memória
     * 
     * @param email Identificador do usuário
     */
    public void delete(String email) {
        redisTemplate.opsForValue().getAndDelete(key(email));
    }

    /**
     * Gera o prefixo da chave para salvamento no redis
     * 
     * @param email Identificador do usuário
     * 
     * @return {@link String} contendo a chave formatada.
     */
    private String key(String email) {
        return PREFIX + email;
    }
}
