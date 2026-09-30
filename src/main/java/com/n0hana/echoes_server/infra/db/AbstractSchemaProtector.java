package com.n0hana.echoes_server.infra.db;

import java.util.List;

import javax.sql.DataSource;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;

import lombok.extern.slf4j.Slf4j;

/**
 * Base para protetores de tabelas críticas via triggers MySQL/MariaDB.
 *
 * <p>
 * Centraliza a mecânica que {@code AuditLogProtector} e
 * {@code FlywayHistoryProtector}
 * compartilham: detecção de SGBD em runtime, obtenção do schema atual, criação
 * idempotente de triggers e tratamento de privilégio ausente.
 * </p>
 *
 * <p>
 * Não confia no dialeto configurado: H2 em {@code MODE=MySQL} reporta
 * {@code "H2"} e MariaDB reporta {@code "MariaDB"}, então a decisão é feita
 * consultando o driver.
 * </p>
 */
@Slf4j
public abstract class AbstractSchemaProtector {

    protected final JdbcTemplate jdbc;
    protected final DataSource dataSource;

    protected AbstractSchemaProtector(JdbcTemplate jdbc, DataSource dataSource) {
        this.jdbc = jdbc;
        this.dataSource = dataSource;
    }

    /** Nome da tabela protegida, usado em logs e checagem de existência. */
    protected abstract String tableName();

    /** Triggers a garantir em cada startup. Idempotente por nome. */
    protected abstract List<TriggerSpec> triggers();

    /**
     * Especificação de um trigger.
     *
     * @param name chave lógica — usada para checar existência em
     *             {@code information_schema}
     * @param body DDL a partir de {@code BEFORE ...}, sem o {@code CREATE TRIGGER}
     */
    protected record TriggerSpec(String name, String body) {
    }

    @EventListener(ApplicationReadyEvent.class)
    public void protect() {
        if (!isMySql()) {
            log.info("{} ignorado: SGBD atual não é MySQL/MariaDB.", getClass().getSimpleName());
            return;
        }

        String schema = currentSchema();
        if (schema == null || schema.isBlank()) {
            log.warn("{}: schema atual não pôde ser determinado. A tabela {} está desprotegida.",
                    getClass().getSimpleName(), tableName());
            return;
        }

        if (!tableExists(schema, tableName())) {
            log.info("{}: tabela {}.{} ainda não existe — proteção ignorada.",
                    getClass().getSimpleName(), schema, tableName());
            return;
        }

        try {
            for (TriggerSpec spec : triggers()) {
                ensureTrigger(schema, spec.name(), spec.body());
            }
            log.info("{}: {} trigger(s) verificada(s) em {}.{}.",
                    getClass().getSimpleName(), triggers().size(), schema, tableName());
        } catch (SuperPrivilegeException e) {
            log.warn("Usuário MySQL sem privilégio SUPER: triggers de {}.{} não criados. "
                    + "Crie-os manualmente ou ajuste o usuário.",
                    schema, tableName());
        } catch (Exception e) {
            log.error("Falha ao criar triggers em {}.{}: {}",
                    schema, tableName(), e.getMessage(), e);
        }
    }

    private boolean isMySql() {
        try (var conn = dataSource.getConnection()) {
            String product = conn.getMetaData().getDatabaseProductName();
            if (product == null) {
                return false;
            }
            String p = product.toLowerCase();
            return p.contains("mysql") || p.contains("mariadb");
        } catch (Exception e) {
            log.warn("Falha ao detectar SGBD em {}: {}",
                    getClass().getSimpleName(), e.getMessage());
            return false;
        }
    }

    private String currentSchema() {
        try {
            return jdbc.queryForObject("SELECT DATABASE()", String.class);
        } catch (Exception e) {
            log.warn("Falha ao obter o schema atual via SELECT DATABASE(): {}", e.getMessage());
            return null;
        }
    }

    private boolean tableExists(String schema, String table) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES "
                        + "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?",
                Integer.class, schema, table);
        return count != null && count > 0;
    }

    /**
     * Garante que o gatilho existe. Se já existir, não faz nada. Se a criação
     * colidir com outra instância subindo ao mesmo tempo, "already exists" é
     * tratado como sucesso — o resultado final é o mesmo.
     */
    private void ensureTrigger(String schema, String name, String body) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TRIGGERS "
                        + "WHERE TRIGGER_NAME = ? AND TRIGGER_SCHEMA = ?",
                Integer.class, name, schema);

        if (count != null && count > 0) {
            log.debug("Trigger {} já existe em {}.", name, schema);
            return;
        }

        try {
            jdbc.execute("CREATE TRIGGER " + name + " " + body);
            log.info("Trigger criado: {} (schema={})", name, schema);
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("already exists")) {
                log.debug("Trigger {} já existia (corrida entre instâncias).", name);
                return;
            }
            if (msg != null && msg.contains("SUPER privilege")) {
                throw new SuperPrivilegeException(e);
            }
            throw e;
        }
    }

    protected static final class SuperPrivilegeException extends RuntimeException {
        SuperPrivilegeException(Throwable cause) {
            super(cause);
        }
    }
}
