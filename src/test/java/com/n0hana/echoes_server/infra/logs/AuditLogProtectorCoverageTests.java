package com.n0hana.echoes_server.infra.logs;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.function.BiFunction;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

@DisplayName("AuditLogProtector: criação dos gatilhos de proteção")
class AuditLogProtectorCoverageTests {

    private JdbcTemplate jdbc;
    private DataSource dataSource;
    private DatabaseMetaData meta;
    private AuditLogProtector protector;

    /** (nome do gatilho, true se a consulta é a de marcador de versão) -> contagem */
    private BiFunction<String, Boolean, Integer> counts;

    @BeforeEach
    void setUp() throws Exception {
        jdbc = mock(JdbcTemplate.class);
        dataSource = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        meta = mock(DatabaseMetaData.class);
        when(dataSource.getConnection()).thenReturn(conn);
        when(conn.getMetaData()).thenReturn(meta);
        when(meta.getDatabaseProductName()).thenReturn("MySQL");
        when(jdbc.queryForObject("SELECT DATABASE()", String.class)).thenReturn("echoes_test");

        counts = (name, marker) -> 0;
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
                .thenAnswer(inv -> counts.apply(inv.getArgument(2), ((String) inv.getArgument(0)).contains("ACTION_STATEMENT")));

        protector = new AuditLogProtector(jdbc, dataSource);
    }

    @Test
    @DisplayName("ignora bancos que não são MySQL/MariaDB")
    void ignoraH2() throws Exception {
        when(meta.getDatabaseProductName()).thenReturn("H2");

        protector.protectAuditLogTable();

        verify(jdbc, never()).execute(anyString());
        verify(jdbc, never()).queryForObject(anyString(), eq(String.class));
    }

    @Test
    @DisplayName("ignora quando o driver não informa o nome do produto")
    void ignoraProdutoNulo() throws Exception {
        when(meta.getDatabaseProductName()).thenReturn(null);

        protector.protectAuditLogTable();

        verifyNoInteractions(jdbc);
    }

    @Test
    @DisplayName("ignora quando não consegue obter conexão")
    void ignoraFalhaDeConexao() throws Exception {
        when(dataSource.getConnection()).thenThrow(new SQLException("sem conexão"));

        assertDoesNotThrow(() -> protector.protectAuditLogTable());

        verifyNoInteractions(jdbc);
    }

    @Test
    @DisplayName("reconhece MariaDB")
    void reconheceMariaDb() throws Exception {
        when(meta.getDatabaseProductName()).thenReturn("MariaDB");

        protector.protectAuditLogTable();

        verify(jdbc).execute(startsWith("CREATE TRIGGER audit_log_no_update"));
        verify(jdbc).execute(startsWith("CREATE TRIGGER audit_log_no_delete"));
    }

    @Test
    @DisplayName("não cria gatilhos quando o schema é nulo")
    void schemaNulo() {
        when(jdbc.queryForObject("SELECT DATABASE()", String.class)).thenReturn(null);

        protector.protectAuditLogTable();

        verify(jdbc, never()).execute(anyString());
    }

    @Test
    @DisplayName("não cria gatilhos quando o schema é vazio")
    void schemaVazio() {
        when(jdbc.queryForObject("SELECT DATABASE()", String.class)).thenReturn("  ");

        protector.protectAuditLogTable();

        verify(jdbc, never()).execute(anyString());
    }

    @Test
    @DisplayName("não cria gatilhos quando SELECT DATABASE() falha")
    void schemaFalha() {
        when(jdbc.queryForObject("SELECT DATABASE()", String.class)).thenThrow(new RuntimeException("falhou"));

        protector.protectAuditLogTable();

        verify(jdbc, never()).execute(anyString());
    }

    @Test
    @DisplayName("cria os dois gatilhos quando não existem")
    void criaGatilhos() {
        protector.protectAuditLogTable();

        verify(jdbc).execute(contains("CREATE TRIGGER audit_log_no_update BEFORE UPDATE ON audit_log"));
        verify(jdbc).execute(contains("CREATE TRIGGER audit_log_no_delete BEFORE DELETE ON audit_log"));
        verify(jdbc, never()).execute(startsWith("DROP"));
    }

    @Test
    @DisplayName("trata contagem nula como gatilho inexistente")
    void contagemNulaCria() {
        counts = (name, marker) -> null;

        protector.protectAuditLogTable();

        verify(jdbc, times(2)).execute(startsWith("CREATE TRIGGER"));
    }

    @Test
    @DisplayName("não faz nada quando os gatilhos já existem na versão atual")
    void gatilhosJaExistem() {
        counts = (name, marker) -> 1;

        protector.protectAuditLogTable();

        verify(jdbc, never()).execute(anyString());
    }

    @Test
    @DisplayName("substitui o gatilho de exclusão de versão antiga (sem marcador)")
    void substituiGatilhoAntigo() {
        counts = (name, marker) -> marker ? 0 : 1;

        protector.protectAuditLogTable();

        verify(jdbc).execute("DROP TRIGGER audit_log_no_delete");
        verify(jdbc).execute(startsWith("CREATE TRIGGER audit_log_no_delete"));
        verify(jdbc, never()).execute("DROP TRIGGER audit_log_no_update");
        verify(jdbc, never()).execute(startsWith("CREATE TRIGGER audit_log_no_update"));
    }

    @Test
    @DisplayName("contagem nula do marcador também força a substituição")
    void marcadorNuloSubstitui() {
        counts = (name, marker) -> marker ? null : 1;

        protector.protectAuditLogTable();

        verify(jdbc).execute("DROP TRIGGER audit_log_no_delete");
    }

    @Test
    @DisplayName("'already exists' (corrida entre réplicas) é tratado como sucesso")
    void corridaEntreInstancias() {
        doThrow(new RuntimeException("Trigger already exists")).when(jdbc).execute(startsWith("CREATE TRIGGER"));

        protector.protectAuditLogTable();

        // não aborta: tenta os dois gatilhos
        verify(jdbc, times(2)).execute(startsWith("CREATE TRIGGER"));
    }

    @Test
    @DisplayName("falta de privilégio SUPER interrompe a criação sem propagar erro")
    void semPrivilegioSuper() {
        doThrow(new RuntimeException("You do not have the SUPER privilege")).when(jdbc)
                .execute(startsWith("CREATE TRIGGER"));

        assertDoesNotThrow(() -> protector.protectAuditLogTable());

        verify(jdbc, times(1)).execute(startsWith("CREATE TRIGGER"));
    }

    @Test
    @DisplayName("outros erros de SQL são registrados e não propagam")
    void erroGenerico() {
        doThrow(new RuntimeException("erro qualquer")).when(jdbc).execute(startsWith("CREATE TRIGGER"));

        assertDoesNotThrow(() -> protector.protectAuditLogTable());

        verify(jdbc, times(1)).execute(startsWith("CREATE TRIGGER"));
    }

    @Test
    @DisplayName("erro sem mensagem não é confundido com 'already exists' nem SUPER")
    void erroSemMensagem() {
        doThrow(new RuntimeException()).when(jdbc).execute(startsWith("CREATE TRIGGER"));

        assertDoesNotThrow(() -> protector.protectAuditLogTable());

        verify(jdbc, times(1)).execute(startsWith("CREATE TRIGGER"));
    }
}
