package com.n0hana.echoes_server.db.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integração real com o Flyway sobre o mesmo banco relacional usado nos
 * testes do projeto (H2 em modo MySQL).
 *
 * <p>Valida o ciclo completo: banco vazio -> migrations -> documentos 1.0.0
 * publicados -> reinicialização sem duplicações (flyway_schema_history).</p>
 */
class FlywayMigrationIntegrationTest {

    private static final String H2_URL = "jdbc:h2:mem:flywaytest;MODE=MySQL;DB_CLOSE_DELAY=-1";

    @BeforeEach
    void setUp() throws Exception {
        try (Connection connection = DriverManager.getConnection(H2_URL, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("DROP ALL OBJECTS");
        }
    }

    private Flyway flyway() {
        return Flyway.configure()
            .dataSource(H2_URL, "sa", "")
            .locations(
                "classpath:db/migration",
                "classpath:com/n0hana/echoes_server/db/migration")
            .load();
    }

    @Test
    void primeiraExecucaoPublicaOsTresDocumentosVersao1_0_0() throws Exception {
        flyway().migrate();

        try (Connection connection = DriverManager.getConnection(H2_URL, "sa", "");
             Statement statement = connection.createStatement()) {

            assertTableCount(statement, "terms", 3L);

            assertPublishedDocuments(statement);
        }
    }

    @Test
    void segundaExecucaoNaoDuplicaDocumentos() throws Exception {
        Flyway flyway = flyway();
        flyway.migrate();
        flyway.migrate();

        try (Connection connection = DriverManager.getConnection(H2_URL, "sa", "");
             Statement statement = connection.createStatement()) {

            // Nenhum documento duplicado após reinicialização.
            assertTableCount(statement, "terms", 3L);

            // V1..V4 executadas exatamente uma vez (installed_rank > 0 ignora a
            // linha de marcador "Flyway Schema History table created").
            assertTableCount(
                statement,
                "\"flyway_schema_history\" WHERE \"installed_rank\" > 0",
                4L);
        }
    }

    private void assertPublishedDocuments(Statement statement) throws Exception {
        expectPublished(statement, "COOKIES_POLICY", "cookies-policy");
        expectPublished(statement, "PRIVACY_POLICY", "privacy-policy");
        expectPublished(statement, "TERMS_OF_USE", "terms");
    }

    private void expectPublished(Statement statement, String type, String folder) throws Exception {
        String expected = new String(
            Files.readAllBytes(Path.of("src/main/resources/legal", folder, "1.0.0.md")),
            StandardCharsets.UTF_8);

        try (ResultSet row = statement.executeQuery(
                "SELECT version, content, status, type, timestamp FROM terms WHERE type = '" + type + "'")) {

            assertThat(row.next()).as("documento %s deve existir", type).isTrue();
            assertThat(row.getString("version")).isEqualTo("1.0.0");
            assertThat(row.getString("status")).isEqualTo("PUBLISHED");
            assertThat(row.getString("type")).isEqualTo(type);
            assertThat(row.getString("content")).isEqualTo(expected);
            assertThat(row.getTimestamp("timestamp")).isNotNull();
        }
    }

    private void assertTableCount(Statement statement, String table, long expected) throws Exception {
        try (ResultSet count = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            count.next();
            assertThat(count.getLong(1)).as("contagem em %s", table).isEqualTo(expected);
        }
    }
}