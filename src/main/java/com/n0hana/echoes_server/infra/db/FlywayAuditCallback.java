package com.n0hana.echoes_server.infra.db;

import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Intercepta a execução das migrations do Flyway para gerar logs de auditoria.
 * 
 * <p>
 * O Spring Boot detecta automaticamente beans do tipo {@link Callback}
 * e os injeta na configuração do Flyway.
 * </p>
 */
@Slf4j
@Component
public class FlywayAuditCallback implements Callback {

    @Override
    public String getCallbackName() {
        return "FlywayAuditCallback";
    }

    @Override
    public boolean supports(Event event, Context context) {
        return event == Event.AFTER_EACH_MIGRATE || event == Event.AFTER_EACH_MIGRATE_ERROR;
    }

    @Override
    public boolean canHandleInTransaction(Event event, Context context) {
        return true;
    }

    @Override
    public void handle(Event event, Context context) {
        var migrationInfo = context.getMigrationInfo();
        if (migrationInfo == null) {
            return;
        }

        String version = migrationInfo.getVersion() != null ? migrationInfo.getVersion().toString() : "N/A";
        String description = migrationInfo.getDescription();

        if (event == Event.AFTER_EACH_MIGRATE) {
            log.info("AUDIT [FLYWAY]: Migration aplicada com sucesso -> {} - {}", version, description);
        } else if (event == Event.AFTER_EACH_MIGRATE_ERROR) {
            log.error("AUDIT [FLYWAY]: FALHA ao aplicar migration -> {} - {}", version, description);
        }
    }
}
