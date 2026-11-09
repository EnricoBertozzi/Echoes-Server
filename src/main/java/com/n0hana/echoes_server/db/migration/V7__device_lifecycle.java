package com.n0hana.echoes_server.db.migration;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * Prepara {@code device_tb} para o ciclo de vida dos dispositivos.
 *
 * <p>
 * É uma migration em Java (e não SQL) porque o Flyway roda antes do Hibernate:
 * em um banco novo a tabela ainda não existe e não há nada a migrar (o
 * Hibernate a cria já no formato final). Em bancos que já têm a tabela:
 * </p>
 * <ul>
 * <li>renomeia {@code mac} para {@code hardware_id};</li>
 * <li>garante {@code status} com os dispositivos existentes como ACTIVE, para
 * que nenhum ESP já em uso seja bloqueado;</li>
 * <li>remove o DEFAULT de {@code status}: novos dispositivos devem gravar o
 * status explicitamente (PENDING).</li>
 * </ul>
 * As demais colunas (institution_id, approved_by, approved_at, last_seen_at) são
 * nulas e adicionadas pelo Hibernate.
 */
public class V7__device_lifecycle extends BaseJavaMigration {

    private static final String TABLE = "device_tb";

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();

        if (!columnExists(connection, "id")) {
            return; // tabela ainda não existe: o Hibernate cria no formato final
        }

        try (Statement statement = connection.createStatement()) {
            if (columnExists(connection, "mac") && !columnExists(connection, "hardware_id")) {
                statement.execute("ALTER TABLE " + TABLE + " RENAME COLUMN mac TO hardware_id");
            }

            if (!columnExists(connection, "status")) {
                statement.execute("ALTER TABLE " + TABLE
                        + " ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'");
            } else {
                statement.execute("UPDATE " + TABLE + " SET status = 'ACTIVE' WHERE status IS NULL");
            }

            statement.execute("ALTER TABLE " + TABLE + " ALTER COLUMN status DROP DEFAULT");
        }
    }

    private boolean columnExists(Connection connection, String column) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        for (String table : new String[] { TABLE, TABLE.toUpperCase() }) {
            for (String name : new String[] { column, column.toUpperCase() }) {
                try (ResultSet columns = metadata.getColumns(connection.getCatalog(), null, table, name)) {
                    if (columns.next()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
