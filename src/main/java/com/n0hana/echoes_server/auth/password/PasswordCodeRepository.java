package com.n0hana.echoes_server.auth.password;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * Repositório para armazenamento em memória de códigos de redefinação de senhas
 * 
 * @author Enrico Bertozzi
 * @since 0.1.1
 */
@Repository
public class PasswordCodeRepository {

    // TODO criar implementação genérica de repositórios em memoria

    private final String PREFIX = "password:code:";

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * Salva o codigo de redefinição de senha de um usuário
     * 
     * @param email Email do usuário
     * @param code  Código de redefinição
     */
    public void save(String email, PasswordCodeModel model) {
        redisTemplate.opsForValue().set(key(email), model);
    }

    /**
     * Busca um código armazenado no banco em memória
     * 
     * @param email Email do usuário
     * 
     * @return {@link Optional} com o código de redefinição de senha
     */
    public Optional<Object> findByEmail(String email) {
        return Optional
                .ofNullable(redisTemplate.opsForValue().get(key(email)));
    }

    /**
     * Remove um código armazenado no banco em memória
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
