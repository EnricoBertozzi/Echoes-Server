package com.n0hana.echoes_server.infra.logs;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.n0hana.echoes_server.device.event.DeviceEventModel;
import com.n0hana.echoes_server.device.event.DeviceEventRepository;
import com.n0hana.echoes_server.device.event.DeviceEventType;

/** L8: o expurgo remove só o que passou do prazo, no banco real do contexto (H2 em MODE=MySQL). */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIf("com.n0hana.echoes_server.support.RedisAvailability#redisReachable")
class AuditRetentionIntegrationTest {

    private static final String TAG = "RETENCAO_" + UUID.randomUUID();

    @Autowired private AuditRetentionService service;
    @Autowired private AuditLogRepository auditLogs;
    @Autowired private DeviceEventRepository deviceEvents;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @AfterEach
    void cleanup() {
        // AuditLog é imutável no Hibernate; a limpeza do teste vai direto no SQL
        jdbc.update("delete from audit_log where action = ?", TAG);
        deviceEvents.deleteAll(deviceEvents.findAll().stream()
                .filter(e -> e.getScenarioId() != null && e.getScenarioId().toString().equals(scenarioKey)).toList());
    }

    private String scenarioKey = UUID.randomUUID().toString();

    private AuditLog audit(Instant when) {
        AuditLog l = new AuditLog();
        l.setAction(TAG);
        l.setEntity("Teste");
        l.setStatus("SUCCESS");
        l.setTimestamp(when);
        l.setSignature("sig");
        return auditLogs.save(l);
    }

    private DeviceEventModel event(LocalDateTime when) {
        DeviceEventModel e = new DeviceEventModel();
        e.setDeviceId(UUID.randomUUID());
        e.setInstitutionId(UUID.randomUUID());
        e.setScenarioId(UUID.fromString(scenarioKey));
        e.setType(DeviceEventType.values()[0]);
        e.setOccurredAt(when);
        e.setOccurredOn(when.toLocalDate());
        return deviceEvents.save(e);
    }

    @Test
    @DisplayName("Remove audit_log e device_event vencidos e mantém os dentro do prazo")
    void purgesOnlyExpiredRows() {
        Instant now = Instant.now();
        AuditLog oldAudit = audit(now.minus(Duration.ofDays(1826)));
        AuditLog freshAudit = audit(now.minus(Duration.ofDays(1824)));
        DeviceEventModel oldEvent = event(LocalDateTime.now().minusDays(1826));
        DeviceEventModel freshEvent = event(LocalDateTime.now().minusDays(10));

        AuditRetentionService.Result result = service.purge();

        assertTrue(result.auditLogDeleted() >= 1);
        assertTrue(result.deviceEventDeleted() >= 1);
        assertFalse(auditLogs.existsById(oldAudit.getId()));
        assertTrue(auditLogs.existsById(freshAudit.getId()));
        assertFalse(deviceEvents.existsById(oldEvent.getId()));
        assertTrue(deviceEvents.existsById(freshEvent.getId()));
    }
}
