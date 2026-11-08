package com.n0hana.echoes_server.auth.attempt;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.n0hana.echoes_server.auth.exception.TooManyAttemptsException;
import com.n0hana.echoes_server.infra.redis.RedisRepository;
import com.n0hana.echoes_server.infra.redis.RedisRepositoryFactory;

/**
 * conta tentativas por e-mail no redis e bloqueia quando passa do limite
 * 
 * <p>
 * a janela abre na primeira tentativa e fecha sozinha (ttl). apagar o código
 * não zera o contador, então pedir outro código não libera nova rodada
 * </p>
 */
@Service
public class AttemptService {

    private final Map<AttemptScope, RedisRepository<String>> stores = new EnumMap<>(AttemptScope.class);

    public AttemptService(RedisRepositoryFactory factory) {
        for (AttemptScope scope : AttemptScope.values()) {
            stores.put(scope, factory.string(scope.getType()));
        }
    }

    /** lança 429 se o e-mail já estourou o limite */
    public void assertNotBlocked(AttemptScope scope, String email) {
        if (stores.get(scope).count(key(email)) >= scope.getMax()) {
            throw new TooManyAttemptsException();
        }
    }

    /** soma uma tentativa e diz se o limite foi atingido agora */
    public boolean register(AttemptScope scope, String email) {
        long total = stores.get(scope).increment(key(email), scope.window());
        return total >= scope.getMax();
    }

    /** zera o contador (usar só quando a etapa deu certo) */
    public void clear(AttemptScope scope, String email) {
        stores.get(scope).delete(key(email));
    }

    // e-mail sempre em minúsculo, senão "A@x.com" e "a@x.com" teriam contadores separados
    private String key(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
