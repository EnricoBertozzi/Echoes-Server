package com.n0hana.echoes_server.user;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import com.n0hana.echoes_server.infra.redis.CodeType;
import com.n0hana.echoes_server.infra.redis.RedisRepository;
import com.n0hana.echoes_server.infra.redis.RedisRepositoryFactory;
import com.n0hana.echoes_server.infra.redis.ObjectType;

/**
 * Convites de cadastro pendentes, guardados no Redis por e-mail.
 *
 * <p>
 * O convite vale o mesmo prazo do código de convite ({@link CodeType#INVITE}, 7 dias).
 * Antes a chave expirava em 15 minutos, enquanto o e-mail e {@code expiresAt}
 * diziam 7 dias.
 * </p>
 *
 * <p>
 * Um índice (sorted set) guarda o e-mail de cada convite com o instante em que ele
 * expira, para que a listagem de pendentes não dependa de varrer as chaves do Redis.
 * Entradas expiradas são removidas na leitura.
 * </p>
 */
@Repository
public class PendingRegistrationRepository {

    static final Duration RETENTION = CodeType.INVITE.getDuration();
    static final String INDEX_KEY = "pending:registration:index";

    private final RedisRepository<PendingRegistration> store;
    private final StringRedisTemplate template;

    public PendingRegistrationRepository(RedisRepositoryFactory factory, StringRedisTemplate template) {
        this.store = factory.typed(ObjectType.PENDING_REGISTRATION, PendingRegistration.class);
        this.template = template;
    }

    public PendingRegistration save(PendingRegistration pending) {
        store.save(pending.email(), pending, RETENTION);
        indexAdd(pending);
        return pending;
    }

    public void savePreservingTtl(PendingRegistration pending) {
        Duration remaining = Duration.between(Instant.now(), pending.createdAt().plus(RETENTION));
        if (!remaining.isNegative() && !remaining.isZero()) {
            store.save(pending.email(), pending, remaining);
        }
    }

    public Optional<PendingRegistration> findByEmail(String email) {
        return store.find(email);
    }

    public void deleteByEmail(String email) {
        store.delete(email);
        template.opsForZSet().remove(INDEX_KEY, email);
    }

    /**
     * Convites ainda válidos. Remove do índice os que já expiraram ou cujo conteúdo
     * sumiu do Redis.
     */
    public List<PendingRegistration> listActive() {
        template.opsForZSet().removeRangeByScore(INDEX_KEY, 0, Instant.now().getEpochSecond());

        Set<String> emails = template.opsForZSet().range(INDEX_KEY, 0, -1);
        List<PendingRegistration> active = new ArrayList<>();
        if (emails == null) {
            return active;
        }

        for (String email : emails) {
            Optional<PendingRegistration> pending = findByEmail(email);
            if (pending.isPresent()) {
                active.add(pending.get());
            } else {
                template.opsForZSet().remove(INDEX_KEY, email);
            }
        }
        return active;
    }

    private void indexAdd(PendingRegistration pending) {
        template.opsForZSet().add(
                INDEX_KEY,
                pending.email(),
                pending.createdAt().plus(RETENTION).getEpochSecond());
    }
}
