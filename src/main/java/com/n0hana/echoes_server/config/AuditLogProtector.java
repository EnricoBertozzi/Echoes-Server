package com.n0hana.echoes_server.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;

import lombok.RequiredArgsConstructor;

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
                "BEFORE UPDATE ON audit_log FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AuditLog entries cannot be modified'"
            );

            createTriggerIfNotExists(schema,
                "audit_log_no_delete",
                "BEFORE DELETE ON audit_log FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AuditLog entries cannot be deleted'"
            );

            System.out.println("AuditLog database triggers created/verified successfully");
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("SUPER privilege")) {
                System.err.println("MySQL user lacks SUPER privilege for CREATE TRIGGER.");
                System.err.println("Run this in your MySQL shell as a root user:");
                System.err.println("  SET GLOBAL log_bin_trust_function_creators = 1;");
                System.err.println("Or grant SUPER to the echoes user:");
                System.err.println("  GRANT SUPER ON *.* TO '" + jdbc.queryForObject("SELECT USER()", String.class) + "';");
                System.err.println("Or create the triggers manually:");
                System.err.println("  CREATE TRIGGER audit_log_no_update BEFORE UPDATE ON audit_log FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AuditLog entries cannot be modified';");
                System.err.println("  CREATE TRIGGER audit_log_no_delete BEFORE DELETE ON audit_log FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AuditLog entries cannot be deleted';");
            } else {
                System.err.println("Failed to create audit_log triggers: " + msg);
                e.printStackTrace();
            }
        }
    }

    private void createTriggerIfNotExists(String schema, String triggerName, String triggerBody) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_NAME = ? AND TRIGGER_SCHEMA = ?",
            Integer.class,
            triggerName, schema
        );

        if (count != null && count == 0) {
            jdbc.execute("CREATE TRIGGER " + triggerName + " " + triggerBody);
            System.out.println("Created trigger: " + triggerName);
        }
    }
}
