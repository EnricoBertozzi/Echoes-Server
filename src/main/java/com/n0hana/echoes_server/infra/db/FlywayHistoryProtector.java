package com.n0hana.echoes_server.infra.db;

import java.util.List;
import javax.sql.DataSource;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Cria os gatilhos que impedem UPDATE e DELETE direto na tabela do Flyway.
 * 
 * <p>
 * Garante a integridade das evoluções do banco de dados, prevenindo que 
 * migrações aplicadas sejam alteradas ou apagadas manualmente, o que 
 * corromperia o estado reportado pela ferramenta e violaria a auditoria.
 * </p>
 */
@Configuration
public class FlywayHistoryProtector extends AbstractSchemaProtector {

    private static final String TABLE = "flyway_schema_history";

    public FlywayHistoryProtector(JdbcTemplate jdbc, DataSource dataSource) {
        super(jdbc, dataSource);
    }

    @Override
    protected String tableName() {
        return TABLE;
    }

    @Override
    protected List<TriggerSpec> triggers() {
        return List.of(
                new TriggerSpec(
                        "flyway_history_no_update",
                        "BEFORE UPDATE ON " + TABLE + " FOR EACH ROW "
                                + "SIGNAL SQLSTATE '45000' "
                                + "SET MESSAGE_TEXT = 'O historico de migrations do Flyway nao pode ser modificado'"),
                new TriggerSpec(
                        "flyway_history_no_delete",
                        "BEFORE DELETE ON " + TABLE + " FOR EACH ROW "
                                + "SIGNAL SQLSTATE '45000' "
                                + "SET MESSAGE_TEXT = 'O historico de migrations do Flyway nao pode ser deletado'"));
    }
}
