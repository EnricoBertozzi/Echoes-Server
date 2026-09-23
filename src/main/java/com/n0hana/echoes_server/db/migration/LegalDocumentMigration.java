package com.n0hana.echoes_server.db.migration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import com.n0hana.echoes_server.term.model.DocumentType;
import com.n0hana.echoes_server.term.model.TermStatus;

/**
 * Base para as Java Migrations que publicam documentos legais
 */
public abstract class LegalDocumentMigration extends BaseJavaMigration {

    protected String readLegalDocument(String resourcePath) {
        try (InputStream in = openResource(resourcePath)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(
                "Falha ao ler documento legal do classpath: " + resourcePath, e);
        }
    }

    private InputStream openResource(String resourcePath) {
        InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath);
        if (in == null) {
            throw new IllegalStateException(
                "Documento legal não encontrado no classpath: " + resourcePath);
        }
        return in;
    }

    protected void publishDocument(
        Context context,
        DocumentType type,
        String version,
        String resourcePath
    ) {
        String content = readLegalDocument(resourcePath);

        String sql = "INSERT INTO terms (id, version, content, timestamp, type, status) "
                   + "VALUES (?, ?, ?, ?, ?, ?)";

        try (PreparedStatement statement = context.getConnection().prepareStatement(sql)) {
            statement.setLong(1, nextTermsId(context.getConnection()));
            statement.setString(2, version);
            statement.setString(3, content);
            statement.setTimestamp(4, Timestamp.from(Instant.now()));
            statement.setString(5, type.getName());
            statement.setString(6, TermStatus.PUBLISHED.getName());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(
                "Falha ao publicar documento legal '" + resourcePath + "' no banco", e);
        }
    }

    private long nextTermsId(Connection connection) throws SQLException {
        String selectSql = "SELECT next_val FROM terms_seq FOR UPDATE";
        try (PreparedStatement select = connection.prepareStatement(selectSql);
             ResultSet resultSet = select.executeQuery()) {

            long next;
            if (resultSet.next()) {
                next = resultSet.getLong(1);
            } else {
                next = 1L;
                String insertSql = "INSERT INTO terms_seq (next_val) VALUES (1)";
                try (PreparedStatement insert = connection.prepareStatement(insertSql)) {
                    insert.executeUpdate();
                }
            }

            String updateSql = "UPDATE terms_seq SET next_val = ?";
            try (PreparedStatement update = connection.prepareStatement(updateSql)) {
                update.setLong(1, next + 1);
                update.executeUpdate();
            }
            return next;
        }
    }
}
