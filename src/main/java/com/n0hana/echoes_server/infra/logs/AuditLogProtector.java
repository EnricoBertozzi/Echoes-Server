package com.n0hana.echoes_server.infra.logs;

import javax.sql.DataSource;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Cria os gatilhos que impedem UPDATE e DELETE direto na tabela audit_log.
 *
 * <p>
 * O {@code @Immutable} e o {@code @PreRemove} do {@link AuditLog} só valem
 * quando a operação passa pelo Hibernate. Se alguém rodar um DELETE no
 * mysql, ou usar JdbcTemplate direto, ou criar um {@code @Modifying} novo
 * num repository, essas anotações não seguram nada. Por isso os gatilhos:
 * são a única camada que o SQL direto não escapa.
 * </p>
 *
 * <p>
 * Antes isso aqui só rodava quando {@code ddl-auto=update}. Só que em
 * produção normalmente é {@code validate} ou {@code none}, então os
 * gatilhos nunca eram criados justamente onde importa. Agora a gente
 * verifica o banco em runtime, e não confia no dialeto configurado —
 * H2 em MODE=MySQL reporta "H2", e MariaDB reporta "MariaDB", não "MySQL".
 * </p>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AuditLogProtector {

    private static final String TABLE = "audit_log";
    private static final String TRIGGER_UPDATE = "audit_log_no_update";
    private static final String TRIGGER_DELETE = "audit_log_no_delete";

    private final JdbcTemplate jdbc;
    private final DataSource dataSource;

    @EventListener(ApplicationReadyEvent.class)
    public void protectAuditLogTable() {
        if (!isMySql()) {
            log.info("AuditLogProtector ignorado: SGBD atual não é MySQL/MariaDB.");
            return;
        }

        String schema = currentSchema();
        if (schema == null || schema.isBlank()) {
            log.warn("AuditLogProtector: schema atual não pôde ser determinado. "
                    + "Os gatilhos de proteção NÃO foram criados — a tabela {} está desprotegida.",
                    TABLE);
            return;
        }

        try {
            ensureTrigger(schema, TRIGGER_UPDATE,
                    "BEFORE UPDATE ON " + TABLE + " FOR EACH ROW "
                            + "SIGNAL SQLSTATE '45000' "
                            + "SET MESSAGE_TEXT = 'Registros de auditoria nao podem ser modificados'");

            ensureTrigger(schema, TRIGGER_DELETE,
                    "BEFORE DELETE ON " + TABLE + " FOR EACH ROW "
                            + "SIGNAL SQLSTATE '45000' "
                            + "SET MESSAGE_TEXT = 'Registros de auditoria nao podem ser deletados'");

            log.info("Gatilhos de proteção do AuditLog verificados (schema={}).", schema);
        } catch (SuperPrivilegeException e) {
            log.warn("Usuário MySQL sem privilégio SUPER: não foi possível criar os gatilhos "
                    + "de proteção na tabela {}. Crie-os manualmente ou ajuste o usuário.",
                    TABLE);
        } catch (Exception e) {
            log.error("Falha ao criar gatilhos de auditoria (schema={}): {}",
                    schema, e.getMessage(), e);
        }
    }

    /**
     * Descobre se o banco é MySQL ou MariaDB perguntando pro driver.
     *
     * <p>
     * Não dá pra confiar no dialeto configurado no properties: H2 em
     * {@code MODE=MySQL} continua sendo H2, e o driver do MariaDB devolve
     * {@code "MariaDB"} — quem procura só por "mysql" pula os dois.
     * </p>
     */
    private boolean isMySql() {
        try (var conn = dataSource.getConnection()) {
            String product = conn.getMetaData().getDatabaseProductName();
            if (product == null) return false;
            String p = product.toLowerCase();
            return p.contains("mysql") || p.contains("mariadb");
        } catch (Exception e) {
            log.warn("Falha ao detectar SGBD para o AuditLogProtector: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Pega o schema atual via {@code SELECT DATABASE()}. Se der ruim,
     * devolve null pra quem chamou decidir o que fazer — o método não
     * tenta adivinhar.
     */
    private String currentSchema() {
        try {
            return jdbc.queryForObject("SELECT DATABASE()", String.class);
        } catch (Exception e) {
            log.warn("Falha ao obter o schema atual via SELECT DATABASE(): {}", e.getMessage());
            return null;
        }
    }

    /**
     * Garante que o gatilho existe. Se já existir, não faz nada. Se a
     * gente tentar criar e outro pod tiver criado no meio do caminho
     * (duas réplicas subindo juntas), o MySQL reclama com "already exists"
     * — tratamos como sucesso, porque o resultado final é o mesmo.
     */
    private void ensureTrigger(String schema, String triggerName, String body) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TRIGGERS "
                        + "WHERE TRIGGER_NAME = ? AND TRIGGER_SCHEMA = ?",
                Integer.class,
                triggerName, schema);

        if (count != null && count > 0) {
            log.debug("Gatilho {} já existe no schema {}.", triggerName, schema);
            return;
        }

        try {
            jdbc.execute("CREATE TRIGGER " + triggerName + " " + body);
            log.info("Gatilho criado: {} (schema={})", triggerName, schema);
        } catch (Exception e) {
            // Outra instância criou o gatilho entre o SELECT e o CREATE.
            if (isAlreadyExists(e)) {
                log.debug("Gatilho {} já existia (corrida entre instâncias).", triggerName);
                return;
            }
            // Usuário sem SUPER: não é bug, é ambiente. Deixa quem chamou
            // decidir se loga warn ou só segue.
            if (isSuperPrivilegeError(e)) {
                throw new SuperPrivilegeException(e);
            }
            throw e;
        }
    }

    private boolean isAlreadyExists(Exception e) {
        String msg = e.getMessage();
        return msg != null && msg.contains("already exists");
    }

    private boolean isSuperPrivilegeError(Exception e) {
        String msg = e.getMessage();
        return msg != null && msg.contains("SUPER privilege");
    }

    private static final class SuperPrivilegeException extends RuntimeException {
        SuperPrivilegeException(Throwable cause) {
            super(cause);
        }
    }
}
