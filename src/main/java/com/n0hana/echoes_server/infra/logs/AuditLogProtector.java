package com.n0hana.echoes_server.infra.logs;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.jpa.hibernate.ddl-auto", havingValue = "update", matchIfMissing = true)
public class AuditLogProtector {

    private final JdbcTemplate jdbc;

    @EventListener(ApplicationReadyEvent.class)
    public void protectAuditLogTable() {
        try {
            String schema = jdbc.queryForObject("SELECT DATABASE()", String.class);

            createTriggerIfNotExists(schema,
                    "audit_log_no_update",
                    "BEFORE UPDATE ON audit_log FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Registros de auditoria nao podem ser modificados'");

            createTriggerIfNotExists(schema,
                    "audit_log_no_delete",
                    "BEFORE DELETE ON audit_log FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Registros de auditoria nao podem ser deletados'");

            log.info("Gatilhos de protecao do AuditLog verificados com sucesso.");
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("SUPER privilege")) {
                log.warn(
                        "Usuario do MySQL nao possui privilegios para criar gatilhos.");
            } else {
                log.error("Falha ao criar gatilhos de auditoria: {}", msg);
            }
        }
    }

    private void createTriggerIfNotExists(String schema, String triggerName, String triggerBody) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_NAME = ? AND TRIGGER_SCHEMA = ?",
                Integer.class,
                triggerName, schema);

        if (count != null && count == 0) {
            jdbc.execute("CREATE TRIGGER " + triggerName + " " + triggerBody);
            log.info("Gatilho criado: {}", triggerName);
        }
    }
}