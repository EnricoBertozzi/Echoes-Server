package com.n0hana.echoes_server.db.migration;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * Remove colunas legadas de classroom_materials (ex.: file_name, topic) que
 * não existem mais na entidade e quebram o INSERT por serem NOT NULL sem default.
 * Idempotente: em banco novo ou já correto, não faz nada.
 */
public class V8__fix_legacy_classroom_materials_columns extends BaseJavaMigration {

    private static final String TABLE = "classroom_materials";

    private static final Set<String> EXPECTED = Set.of(
        "id", "classroom_id", "author_id", "title", "description",
        "original_file_name", "stored_file_name", "content_type",
        "file_size", "created_at", "updated_at");

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        List<String> legacy = new ArrayList<>();

        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM " + TABLE + " WHERE 1 = 0")) {
            ResultSetMetaData md = rs.getMetaData();
            for (int i = 1; i <= md.getColumnCount(); i++) {
                String name = md.getColumnName(i);
                if (!EXPECTED.contains(name.toLowerCase())) {
                    legacy.add(name);
                }
            }
        } catch (SQLException tableDoesNotExistYet) {
            return; // banco novo: o Hibernate (ddl-auto) cria a tabela depois
        }

        for (String column : legacy) {
            try (Statement st = connection.createStatement()) {
                st.execute("ALTER TABLE " + TABLE + " DROP COLUMN " + column);
            }
        }
    }
}
