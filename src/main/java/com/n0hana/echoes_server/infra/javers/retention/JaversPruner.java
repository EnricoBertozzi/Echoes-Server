package com.n0hana.echoes_server.infra.javers.retention;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.n0hana.echoes_server.infra.logs.Auditable;

import lombok.RequiredArgsConstructor;

/**
 * Poda o histórico do Javers por tipo de entidade. A ordem respeita as chaves estrangeiras:
 * snapshots primeiro, depois propriedades de commit, identificadores globais e commits órfãos.
 */
// @Component (não @Repository): a tradução de exceções de persistência trocaria a IllegalArgumentException
@Component
@RequiredArgsConstructor
public class JaversPruner {

    static final int BATCH_SIZE = 1000;

    private final JdbcTemplate jdbc;
    private final JaversTypeResolver types;

    /**
     * @param keepMinSnapshots quantos snapshots mais recentes de cada entidade são sempre mantidos
     * @return total de linhas removidas das tabelas do Javers
     */
    @Transactional
    @Auditable(action = "JAVERS_RETENTION_PRUNED", entity = "JaversRetentionPolicy",
            details = "'entityType=' + #entityType + ',deleted=' + #result")
    public int pruneEntity(String entityType, Instant olderThan, int keepMinSnapshots) {
        if (entityType == null || entityType.isBlank())
            throw new IllegalArgumentException("entityType é obrigatório");
        if (olderThan == null || !olderThan.isBefore(Instant.now()))
            throw new IllegalArgumentException("olderThan precisa ser uma data no passado");
        if (keepMinSnapshots < 0)
            throw new IllegalArgumentException("keepMinSnapshots não pode ser negativo");

        List<String> typeNames = types.concreteTypeNames(entityType);
        int removed = deleteSnapshots(typeNames, olderThan, keepMinSnapshots);

        // 2. propriedades de commits que perderam todos os snapshots
        removed += jdbc.update("""
                DELETE FROM jv_commit_property WHERE commit_fk NOT IN (
                    SELECT commit_fk FROM jv_snapshot WHERE commit_fk IS NOT NULL)""");

        // 3. identificadores globais sem nenhum snapshot (entidade extinta), que não sejam donos de outro id
        removed += jdbc.update("""
                DELETE FROM jv_global_id
                WHERE global_id_pk NOT IN (SELECT global_id_fk FROM jv_snapshot WHERE global_id_fk IS NOT NULL)
                  AND global_id_pk NOT IN (
                      SELECT owner_id_fk FROM (SELECT owner_id_fk FROM jv_global_id WHERE owner_id_fk IS NOT NULL) owners)""");

        // 4. commits que nenhum snapshot ou propriedade referencia mais
        removed += jdbc.update("""
                DELETE FROM jv_commit
                WHERE commit_pk NOT IN (SELECT commit_fk FROM jv_snapshot WHERE commit_fk IS NOT NULL)
                  AND commit_pk NOT IN (SELECT commit_fk FROM jv_commit_property WHERE commit_fk IS NOT NULL)""");

        return removed;
    }

    private int deleteSnapshots(List<String> typeNames, Instant olderThan, int keep) {
        String placeholders = typeNames.stream().map(t -> "?").collect(Collectors.joining(","));
        String cutoff = DateTimeFormatter.ISO_INSTANT.format(olderThan.truncatedTo(ChronoUnit.SECONDS));

        String select = """
                SELECT s.snapshot_pk FROM jv_snapshot s
                  JOIN jv_commit c ON c.commit_pk = s.commit_fk
                  JOIN jv_global_id g ON g.global_id_pk = s.global_id_fk
                WHERE g.type_name IN (%s)
                  AND c.commit_date_instant < ?
                  AND (SELECT COUNT(*) FROM jv_snapshot n
                        WHERE n.global_id_fk = s.global_id_fk AND n.snapshot_pk > s.snapshot_pk) >= ?
                LIMIT %d""".formatted(placeholders, BATCH_SIZE);

        List<Object> params = new ArrayList<>(typeNames);
        params.add(cutoff);
        params.add(keep);

        int total = 0;
        while (true) {
            List<Long> ids = jdbc.queryForList(select, Long.class, params.toArray());
            if (ids.isEmpty())
                return total;

            String in = ids.stream().map(i -> "?").collect(Collectors.joining(","));
            total += jdbc.update("DELETE FROM jv_snapshot WHERE snapshot_pk IN (" + in + ")", ids.toArray());
        }
    }
}
