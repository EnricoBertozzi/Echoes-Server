package com.n0hana.echoes_server.infra.javers.anonymization;

import java.util.List;
import java.util.UUID;

import org.javers.core.Javers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.n0hana.echoes_server.infra.javers.retention.JaversTypeResolver;
import com.n0hana.echoes_server.infra.logs.Auditable;
import com.n0hana.echoes_server.user.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class JaversAnonymizationService {

    static final String USER_TYPE = "com.n0hana.echoes_server.user.model.User";
    static final String ANONYMOUS_NAME = "Anonimizado";

    private final Javers javers;
    private final UserRepository users;
    private final JdbcTemplate jdbc;
    private final JaversTypeResolver types;
    private final ObjectMapper mapper = new ObjectMapper();

    @Async("taskExecutor")
    @Transactional
    @Auditable(action = "JAVERS_ANONYMIZED", entity = "User", entityId = "#userId")
    public void anonymizeAfterSoftDelete(UUID userId) {
        var user = users.findById(userId).orElse(null);
        if (user == null) {
            log.warn("Anonimização do histórico ignorada: usuário {} não encontrado", userId);
            return;
        }

        // garante que o estado atual (já anonimizado) está no histórico antes de reescrever o resto
        javers.commit("system:lgpd-anonymization", user);

        String email = "deleted_user_" + userId + "@anon.invalid";
        String placeholders = String.join(",", types.concreteTypeNames(USER_TYPE).stream().map(t -> "?").toList());
        List<Object> params = new java.util.ArrayList<>();
        params.add("\"" + userId + "\"");
        params.addAll(types.concreteTypeNames(USER_TYPE));

        List<Long> globalIds = jdbc.queryForList(
                "SELECT global_id_pk FROM jv_global_id WHERE local_id = ? AND type_name IN (" + placeholders + ")",
                Long.class, params.toArray());

        int rewritten = 0;
        for (Long globalId : globalIds) {
            for (var row : jdbc.queryForList("SELECT snapshot_pk, state FROM jv_snapshot WHERE global_id_fk = ?", globalId)) {
                String state = (String) row.get("state");
                String updated = anonymizeState(state, email);
                if (updated != null) {
                    jdbc.update("UPDATE jv_snapshot SET state = ? WHERE snapshot_pk = ?", updated, row.get("snapshot_pk"));
                    rewritten++;
                }
            }
        }
        log.info("Histórico do usuário {} anonimizado: {} snapshot(s) reescrito(s)", userId, rewritten);
    }

    /** @return o novo estado, ou null se o snapshot não tem e-mail nem nome para trocar */
    public String anonymizeState(String state, String email) {
        try {
            JsonNode root = mapper.readTree(state);
            if (!(root instanceof ObjectNode node) || !(node.has("email") || node.has("name")))
                return null;

            if (node.has("email"))
                node.put("email", email);
            if (node.has("name") && !node.get("name").isNull())
                node.put("name", ANONYMOUS_NAME);
            return mapper.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalStateException("Estado de snapshot ilegível durante a anonimização", e);
        }
    }
}
