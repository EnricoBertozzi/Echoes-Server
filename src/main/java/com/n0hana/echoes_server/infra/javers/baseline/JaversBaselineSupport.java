package com.n0hana.echoes_server.infra.javers.baseline;

import java.util.function.Function;

import org.javers.core.Javers;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import lombok.extern.slf4j.Slf4j;

/** Registra o estado atual das entidades que ainda não têm histórico, em uma transação por tipo. */
@Slf4j
@Component
public class JaversBaselineSupport {

    static final String AUTHOR = "system:baseline";

    private final Javers javers;
    private final TransactionTemplate tx;

    public JaversBaselineSupport(Javers javers, PlatformTransactionManager transactionManager) {
        this.javers = javers;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /** @return quantas entidades receberam o commit inicial */
    public <T> int baseline(String label, Iterable<T> source, Function<T, Object> idOf) {
        try {
            Integer created = tx.execute(status -> {
                int count = 0;
                for (T entity : source) {
                    if (javers.getLatestSnapshot(idOf.apply(entity), entity.getClass()).isEmpty()) {
                        javers.commit(AUTHOR, entity);
                        count++;
                    }
                }
                return count;
            });
            return created == null ? 0 : created;
        } catch (Exception e) {
            log.error("Falha ao registrar o estado inicial de {} no Javers", label, e);
            return 0;
        }
    }
}
