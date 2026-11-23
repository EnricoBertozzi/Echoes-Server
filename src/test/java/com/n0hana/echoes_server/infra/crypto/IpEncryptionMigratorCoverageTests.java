package com.n0hana.echoes_server.infra.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowCallbackHandler;

import com.n0hana.echoes_server.term.model.UserTermAcceptance;

@DisplayName("IpEncryptionMigrator: migração dos IPs de aceite")
class IpEncryptionMigratorCoverageTests {

    private JdbcTemplate jdbc;
    private DataSource dataSource;
    private Connection connection;
    private DatabaseMetaData meta;
    private AesGcmCipher cipher;
    private IpEncryptionMigrator migrator;

    /** nomes de tabela (como o driver os devolve) que "existem" */
    private Set<String> existingTables;

    private static String randomKey() {
        byte[] raw = new byte[32];
        new SecureRandom().nextBytes(raw);
        return Base64.getEncoder().encodeToString(raw);
    }

    @BeforeEach
    void setUp() throws Exception {
        jdbc = mock(JdbcTemplate.class);
        dataSource = mock(DataSource.class);
        connection = mock(Connection.class);
        meta = mock(DatabaseMetaData.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(meta);
        when(meta.getDatabaseProductName()).thenReturn("H2");

        existingTables = Set.of("user_terms_acceptance", "audit_log");
        when(meta.getTables(any(), any(), anyString(), any())).thenAnswer(inv -> {
            ResultSet rs = mock(ResultSet.class);
            when(rs.next()).thenReturn(existingTables.contains((String) inv.getArgument(2)));
            return rs;
        });

        cipher = new AesGcmCipher(new DataEncryptionKeys(randomKey(), "k1", "", randomKey(), randomKey()));
        migrator = new IpEncryptionMigrator(jdbc, dataSource, cipher);
    }

    private void pendingRows(String... ips) {
        org.mockito.Mockito.doAnswer(inv -> {
            RowCallbackHandler handler = inv.getArgument(1);
            for (String ip : ips) {
                ResultSet rs = mock(ResultSet.class);
                when(rs.getString("ip_address")).thenReturn(ip);
                when(rs.getObject("id")).thenReturn(UUID.randomUUID());
                handler.processRow(rs);
            }
            return null;
        }).when(jdbc).query(anyString(), any(RowCallbackHandler.class));
    }

    @Test
    @DisplayName("sem a tabela de aceites, não faz nada")
    void semTabela() {
        existingTables = Set.of();

        migrator.migrate();

        verifyNoInteractions(jdbc);
    }

    @Test
    @DisplayName("encontra a tabela pelo nome em maiúsculas (H2)")
    void tabelaEmMaiusculas() {
        existingTables = Set.of("USER_TERMS_ACCEPTANCE");
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);

        migrator.migrate();

        verify(jdbc).query(anyString(), any(RowCallbackHandler.class));
    }

    @Test
    @DisplayName("falha ao consultar metadados equivale a tabela inexistente")
    void falhaNosMetadados() throws Exception {
        when(dataSource.getConnection()).thenThrow(new SQLException("indisponível"));

        migrator.migrate();

        verifyNoInteractions(jdbc);
    }

    @Test
    @DisplayName("cifra os IPs em claro e grava em lotes de 500")
    void cifraEmLotes() {
        String[] ips = new String[501];
        for (int i = 0; i < ips.length; i++) {
            ips[i] = "203.0.113." + (i % 250);
        }
        pendingRows(ips);
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);

        migrator.migrate();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Object[]>> batches = ArgumentCaptor.forClass(List.class);
        verify(jdbc, times(2)).batchUpdate(eq("UPDATE user_terms_acceptance SET ip_address = ? WHERE id = ?"),
                batches.capture());
        assertEquals(500, batches.getAllValues().get(0).size());
        assertEquals(1, batches.getAllValues().get(1).size());

        Object[] first = batches.getAllValues().get(0).get(0);
        assertTrue(AesGcmCipher.isEncrypted((String) first[0]));
        assertEquals("203.0.113.0", cipher.decrypt((String) first[0], AcceptanceIpConverter.CONTEXT));
    }

    @Test
    @DisplayName("encryptLegacyAcceptanceIps devolve 0 e não grava quando nada está pendente")
    void nadaPendente() {
        assertEquals(0, migrator.encryptLegacyAcceptanceIps());

        verify(jdbc, never()).batchUpdate(anyString(), any(List.class));
    }

    @Test
    @DisplayName("conta os registros de auditoria legados sem alterá-los")
    void contaAuditoriaLegada() {
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(7L);

        migrator.migrate();

        verify(jdbc, never()).execute(anyString());
        verify(jdbc).queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE ip IS NOT NULL AND ip NOT LIKE 'enc:%'", Long.class);
    }

    @Test
    @DisplayName("contagem nula de auditoria é tratada como zero")
    void contagemAuditoriaNula() {
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(null);

        migrator.migrate();

        verify(jdbc).queryForObject(anyString(), eq(Long.class));
    }

    @Test
    @DisplayName("sem tabela audit_log, não consulta a contagem")
    void semTabelaAuditoria() {
        existingTables = Set.of("user_terms_acceptance");

        migrator.migrate();

        verify(jdbc, never()).queryForObject(anyString(), eq(Long.class));
    }

    @SuppressWarnings("unchecked")
    private void mysqlColumnLength(Integer length) throws Exception {
        when(meta.getDatabaseProductName()).thenReturn("MySQL");
        when(jdbc.query(anyString(), any(ResultSetExtractor.class))).thenReturn(length);
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);
    }

    @Test
    @DisplayName("MySQL: amplia a coluna quando ela é menor que o necessário")
    void ampliaColuna() throws Exception {
        mysqlColumnLength(45);

        migrator.migrate();

        verify(jdbc).execute("ALTER TABLE user_terms_acceptance MODIFY ip_address VARCHAR("
                + UserTermAcceptance.ENCRYPTED_IP_COLUMN_LENGTH + ")");
    }

    @Test
    @DisplayName("MySQL: não altera a coluna que já é grande o bastante")
    void colunaJaAmpla() throws Exception {
        mysqlColumnLength(UserTermAcceptance.ENCRYPTED_IP_COLUMN_LENGTH);

        migrator.migrate();

        verify(jdbc, never()).execute(anyString());
    }

    @Test
    @DisplayName("MySQL: coluna não encontrada não gera ALTER")
    void colunaNaoEncontrada() throws Exception {
        mysqlColumnLength(null);

        migrator.migrate();

        verify(jdbc, never()).execute(anyString());
    }

    @Test
    @DisplayName("falha ao detectar o SGBD é tratada como não-MySQL")
    void falhaAoDetectarSgbd() throws Exception {
        // 1ª conexão: tableExists (user_terms_acceptance); 2ª: isMySql; demais: audit_log
        when(dataSource.getConnection()).thenReturn(connection).thenThrow(new SQLException("falhou"))
                .thenReturn(connection);
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);

        migrator.migrate();

        verify(jdbc, never()).execute(anyString());
        verify(jdbc, never()).query(contains(), any(ResultSetExtractor.class));
    }

    private static String contains() {
        return org.mockito.ArgumentMatchers.contains("information_schema");
    }
}
