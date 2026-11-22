package com.n0hana.echoes_server.infra.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.n0hana.echoes_server.infra.logs.AuditLog;
import com.n0hana.echoes_server.infra.logs.AuditLogRepository;
import com.n0hana.echoes_server.infra.logs.AuditSigner;
import com.n0hana.echoes_server.term.TermRepository;
import com.n0hana.echoes_server.term.UserTermAcceptanceRepository;
import com.n0hana.echoes_server.term.model.DocumentType;
import com.n0hana.echoes_server.term.model.TermModel;
import com.n0hana.echoes_server.term.model.TermStatus;
import com.n0hana.echoes_server.term.model.UserTermAcceptance;
import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.model.Student;
import com.n0hana.echoes_server.user.model.User;

import jakarta.persistence.EntityManager;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class IpEncryptionIntegrationTest {

    private static final String IP = "203.0.113.77";

    @Autowired private UserRepository users;
    @Autowired private TermRepository terms;
    @Autowired private UserTermAcceptanceRepository acceptances;
    @Autowired private AuditLogRepository auditLogs;
    @Autowired private AuditSigner signer;
    @Autowired private IpEncryptionMigrator migrator;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager em;

    @Test
    @DisplayName("IP do aceite fica cifrado na coluna e volta em claro na entidade")
    void acceptanceIpIsEncryptedAtRest() {
        UserTermAcceptance saved = acceptances.saveAndFlush(
            new UserTermAcceptance(newUser(), term(), IP, "EchoesTest/1.0"));
        em.clear();

        String raw = rawAcceptanceIp(saved.getId());
        assertTrue(raw.startsWith("enc:v1:"), "coluna deve guardar o valor cifrado: " + raw);
        assertFalse(raw.contains(IP));

        assertEquals(IP, acceptances.findById(saved.getId()).orElseThrow().getIpAddress());
    }

    @Test
    @DisplayName("IP da auditoria fica cifrado e a assinatura HMAC continua válida")
    void auditIpIsEncryptedAndSignatureHolds() {
        AuditLog log = AuditLog.builder()
            .userId(UUID.randomUUID().toString())
            .action("CRYPTO_TEST")
            .entity("User")
            .status("SUCCESS")
            .ip(IP)
            .build();
        log.setTimestamp(Instant.now().truncatedTo(ChronoUnit.SECONDS));
        log.setSignature(signer.sign(log));
        AuditLog saved = auditLogs.saveAndFlush(log);
        em.clear();

        String raw = jdbc.queryForObject("SELECT ip FROM audit_log WHERE id = ?", String.class, id(saved.getId()));
        assertTrue(raw.startsWith("enc:v1:"), "coluna deve guardar o valor cifrado: " + raw);
        assertFalse(raw.contains(IP));

        AuditLog reloaded = auditLogs.findById(saved.getId()).orElseThrow();
        assertEquals(IP, reloaded.getIp());
        assertTrue(signer.verify(reloaded), "a verificação de integridade deve continuar valendo");
    }

    @Test
    @DisplayName("Registro de auditoria antigo, em claro, continua legível e com assinatura válida")
    void legacyAuditRowStillReadable() {
        AuditLog log = AuditLog.builder().action("LEGACY").entity("User").status("SUCCESS").ip(IP).build();
        log.setTimestamp(Instant.now().truncatedTo(ChronoUnit.SECONDS));
        log.setSignature(signer.sign(log));
        AuditLog current = auditLogs.saveAndFlush(log);

        // simula um registro gravado antes da criptografia: cópia por INSERT direto com o
        // IP em claro (o UPDATE é barrado pelos gatilhos do audit_log no MySQL). Timestamp e
        // assinatura vêm do registro gravado pelo Hibernate, sem depender de fuso horário.
        Object rawTimestamp = jdbc.queryForObject(
            "SELECT timestamp FROM audit_log WHERE id = ?", Object.class, id(current.getId()));
        UUID legacyId = UUID.randomUUID();
        jdbc.update("INSERT INTO audit_log (id, action, entity, status, ip, timestamp, signature) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)",
            id(legacyId), "LEGACY", "User", "SUCCESS", IP, rawTimestamp, current.getSignature());
        em.clear();

        assertEquals(IP, jdbc.queryForObject("SELECT ip FROM audit_log WHERE id = ?", String.class, id(legacyId)),
            "o registro simulado deve estar em claro no banco");

        AuditLog reloaded = auditLogs.findById(legacyId).orElseThrow();
        assertEquals(IP, reloaded.getIp());
        assertTrue(signer.verify(reloaded));
    }

    @Test
    @DisplayName("Migrador cifra IPs de aceite em claro e é idempotente")
    void migratorEncryptsLegacyAcceptances() {
        UserTermAcceptance saved = acceptances.saveAndFlush(
            new UserTermAcceptance(newUser(), term(), IP, "EchoesTest/1.0"));
        jdbc.update("UPDATE user_terms_acceptance SET ip_address = ? WHERE id = ?", IP, id(saved.getId()));
        em.clear();

        // antes da migração o valor legado é lido normalmente
        assertEquals(IP, acceptances.findById(saved.getId()).orElseThrow().getIpAddress());
        em.clear();

        assertTrue(migrator.encryptLegacyAcceptanceIps() >= 1);
        assertTrue(rawAcceptanceIp(saved.getId()).startsWith("enc:v1:"));
        assertEquals(0, migrator.encryptLegacyAcceptanceIps(), "segunda execução não deve alterar nada");

        assertEquals(IP, acceptances.findById(saved.getId()).orElseThrow().getIpAddress());
    }

    private String rawAcceptanceIp(UUID id) {
        return jdbc.queryForObject("SELECT ip_address FROM user_terms_acceptance WHERE id = ?", String.class, id(id));
    }

    /**
     * Parâmetro de id para SQL direto: o H2 tem tipo UUID nativo; o MySQL guarda o
     * UUID do Hibernate em BINARY(16).
     */
    private Object id(UUID id) {
        Boolean mysql = jdbc.execute((java.sql.Connection c) ->
            c.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql"));
        if (!Boolean.TRUE.equals(mysql)) {
            return id;
        }
        return java.nio.ByteBuffer.allocate(16)
            .putLong(id.getMostSignificantBits())
            .putLong(id.getLeastSignificantBits())
            .array();
    }

    private User newUser() {
        Student s = new Student();
        s.setEmail("crypto-" + UUID.randomUUID() + "@test.local");
        s.setName("Titular Cripto");
        s.setPassword("hash");
        return users.saveAndFlush(s);
    }

    private TermModel term() {
        return terms.findFirstByTypeAndStatusOrderByTimestampDesc(DocumentType.TERMS_OF_USE, TermStatus.PUBLISHED)
            .orElseGet(() -> {
                TermModel t = new TermModel();
                t.setType(DocumentType.TERMS_OF_USE);
                t.setVersion("1.0.0");
                t.setStatus(TermStatus.PUBLISHED);
                t.setContent("# termos");
                return terms.saveAndFlush(t);
            });
    }
}
