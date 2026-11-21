package com.n0hana.echoes_server.infra.javers.retention;

import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class JaversRetentionService {

    private final JaversRetentionPolicyRepository policies;
    private final JaversPruner pruner;

    @Value("${javers.retention.min-snapshots-per-entity:10}")
    private int minSnapshotsPerEntity = 10;

    /** Aplica todas as políticas ativas. Falha em um tipo não impede os demais. */
    public int pruneAll() {
        Instant now = Instant.now();
        int total = 0;

        for (JaversRetentionPolicy policy : policies.findByActiveTrue()) {
            Instant olderThan = now.minus(Duration.ofDays(policy.getRetentionDays()));
            Instant hotCutoff = now.minus(Duration.ofDays(policy.getHotWindowDays()));
            if (olderThan.isAfter(hotCutoff))
                olderThan = hotCutoff;

            try {
                total += pruner.pruneEntity(policy.getEntityType(), olderThan, minSnapshotsPerEntity);
            } catch (RuntimeException e) {
                log.error("Falha ao podar o histórico de {}", policy.getEntityType(), e);
            }
        }

        log.info("Poda do Javers concluída: {} linhas removidas", total);
        return total;
    }
}
