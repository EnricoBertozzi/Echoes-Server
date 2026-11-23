package com.n0hana.echoes_server.infra.crypto;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.n0hana.echoes_server.term.model.UserTermAcceptance;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@DependsOn("entityManagerFactory")
@RequiredArgsConstructor
public class IpEncryptionMigrator {

    private static final int BATCH = 500;

    private final JdbcTemplate jdbc;
    private final DataSource dataSource;
    private final AesGcmCipher cipher;

    @PostConstruct
    public void migrate() {
        if (!tableExists("user_terms_acceptance")) {
            return;
        }
        widenAcceptanceIpColumn();
        int migrated = encryptLegacyAcceptanceIps();
        long legacyAudit = countLegacyAuditIps();

        if (migrated > 0 || legacyAudit > 0) {
            log.info("Criptografia de IP: {} aceite(s) migrado(s); {} registro(s) antigo(s) de auditoria "
                + "permanecem em claro até o expurgo de retenção (audit_log é imutável)", migrated, legacyAudit);
        }
    }

    /** cifra os IPs de aceite ainda em claro; devolve quantos foram migrados */
    int encryptLegacyAcceptanceIps() {
        List<Object[]> pending = new ArrayList<>();
        jdbc.query(
            "SELECT id, ip_address FROM user_terms_acceptance "
                + "WHERE ip_address IS NOT NULL AND ip_address NOT LIKE 'enc:%'",
            rs -> {
                pending.add(new Object[] {
                    cipher.encrypt(rs.getString("ip_address"), AcceptanceIpConverter.CONTEXT),
                    rs.getObject("id")
                });
            });

        for (int from = 0; from < pending.size(); from += BATCH) {
            jdbc.batchUpdate("UPDATE user_terms_acceptance SET ip_address = ? WHERE id = ?",
                pending.subList(from, Math.min(from + BATCH, pending.size())));
        }
        return pending.size();
    }

    private void widenAcceptanceIpColumn() {
        if (!isMySql()) {
            return; // H2 dos testes: o schema é criado do zero com o tamanho da anotação
        }
        Integer length = jdbc.query(
            "SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_terms_acceptance' "
                + "AND COLUMN_NAME = 'ip_address'",
            rs -> rs.next() ? rs.getInt(1) : null);

        if (length != null && length < UserTermAcceptance.ENCRYPTED_IP_COLUMN_LENGTH) {
            jdbc.execute("ALTER TABLE user_terms_acceptance MODIFY ip_address VARCHAR("
                + UserTermAcceptance.ENCRYPTED_IP_COLUMN_LENGTH + ")");
            log.info("Coluna user_terms_acceptance.ip_address ampliada de {} para {} caracteres",
                length, UserTermAcceptance.ENCRYPTED_IP_COLUMN_LENGTH);
        }
    }

    private long countLegacyAuditIps() {
        if (!tableExists("audit_log")) {
            return 0;
        }
        Long count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit_log WHERE ip IS NOT NULL AND ip NOT LIKE 'enc:%'", Long.class);
        return count == null ? 0 : count;
    }

    private boolean tableExists(String table) {
        try (Connection connection = dataSource.getConnection()) {
            var meta = connection.getMetaData();
            for (String name : List.of(table, table.toUpperCase())) {
                try (var rs = meta.getTables(connection.getCatalog(), null, name, new String[] { "TABLE" })) {
                    if (rs.next()) {
                        return true;
                    }
                }
            }
            return false;
        } catch (Exception e) {
            log.warn("Não foi possível verificar a tabela {}: {}", table, e.getMessage());
            return false;
        }
    }

    private boolean isMySql() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql");
        } catch (Exception e) {
            return false;
        }
    }
}
