package com.n0hana.echoes_server.db.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;

import com.n0hana.echoes_server.term.model.DocumentType;

/**
 * Testes unitários da infraestrutura de migração de documentos legais:
 * leitura do Markdown pelo classpath, encoding UTF-8, falha quando o recurso
 * não existe e inserção correta preservando o conteúdo.
 */
class LegalDocumentMigrationTest {

    private static final String H2_URL = "jdbc:h2:mem:migrationunit;MODE=MySQL;DB_CLOSE_DELAY=-1";

    private static final Configuration CONFIGURATION =
        Flyway.configure().dataSource(H2_URL, "sa", "").load().getConfiguration();

    /** Migration real (V4) usada para exercitar os métodos protegidos da base. */
    private final V4__publish_terms_1_0_0 migration = new V4__publish_terms_1_0_0();

    @Test
    void readLegalDocumentDeveLerMarkdownUtf8SemAlterarConteudo() throws Exception {
        String expected = readFromDisk("legal/terms/1.0.0.md");

        String content = migration.readLegalDocument("legal/terms/1.0.0.md");

        assertThat(content).isEqualTo(expected);
        // Prova que o conteúdo real (pt-BR) foi lido, não um placeholder.
        assertThat(content).contains("Política");
    }

    @Test
    void readLegalDocumentDeveFalharQuandoRecursoNaoExiste() {
        assertThatThrownBy(() -> migration.readLegalDocument("legal/terms/99.0.0.md"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("legal/terms/99.0.0.md");
    }

    @Test
    void publishDocumentDeveInserirDocumentoPreservandoConteudo() throws Exception {
        createSchema();

        String expected = readFromDisk("legal/terms/1.0.0.md");

        try (Connection connection = DriverManager.getConnection(H2_URL, "sa", "")) {
            publish(connection, DocumentType.TERMS_OF_USE, "1.0.0", "legal/terms/1.0.0.md");

            try (Statement statement = connection.createStatement();
                 ResultSet row = statement.executeQuery(
                     "SELECT id, version, content, type, status, timestamp FROM terms")) {

                assertThat(row.next()).isTrue();
                assertThat(row.getLong("id")).isEqualTo(1L);
                assertThat(row.getString("version")).isEqualTo("1.0.0");
                assertThat(row.getString("type")).isEqualTo("TERMS_OF_USE");
                assertThat(row.getString("status")).isEqualTo("PUBLISHED");
                assertThat(row.getString("content")).isEqualTo(expected);
                assertThat(row.getTimestamp("timestamp")).isNotNull();
                assertThat(row.next()).isFalse();
            }

            try (Statement statement = connection.createStatement();
                 ResultSet sequence = statement.executeQuery("SELECT next_val FROM terms_seq")) {
                assertThat(sequence.next()).isTrue();
                assertThat(sequence.getLong(1)).isEqualTo(2L);
            }
        }
    }

    private void publish(Connection connection, DocumentType type, String version, String resourcePath) {
        migration.publishDocument(new Context() {
            @Override
            public Connection getConnection() {
                return connection;
            }

            @Override
            public Configuration getConfiguration() {
                return CONFIGURATION;
            }
        }, type, version, resourcePath);
    }

    /** Estrutura idêntica à migration V1__create_legal_documents_table.sql. */
    private void createSchema() throws Exception {
        try (Connection connection = DriverManager.getConnection(H2_URL, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("DROP ALL OBJECTS");
            statement.execute("CREATE TABLE IF NOT EXISTS terms ("
                + "id bigint NOT NULL,"
                + "content text,"
                + "status enum('APPROVED','ARCHIVED','DRAFT','IN_REVIEW','PUBLISHED') DEFAULT NULL,"
                + "timestamp datetime(6) DEFAULT NULL,"
                + "type enum('COOKIES_POLICY','DATA_DELETION_POLICY','MARKETING_CONSENT','PRIVACY_POLICY','TERMS_OF_USE') DEFAULT NULL,"
                + "version varchar(255) DEFAULT NULL,"
                + "PRIMARY KEY (id))");
            statement.execute("CREATE TABLE IF NOT EXISTS terms_seq (next_val bigint DEFAULT NULL)");
        }
    }

    /** Fonte independente de comparação: o arquivo no disco (não o classpath). */
    private String readFromDisk(String resourcePath) throws Exception {
        return new String(
            Files.readAllBytes(Path.of("src/main/resources", resourcePath)),
            StandardCharsets.UTF_8);
    }
}