package com.n0hana.echoes_server.infra.logs;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.n0hana.echoes_server.device.event.DeviceEventRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AuditRetentionService {

    /** Piso legal do audit_log em dias (mesmo valor gravado no gatilho do MySQL). */
    public static final int AUDIT_LOG_MIN_DAYS = 1825;

    public record Result(int auditLogDeleted, int deviceEventDeleted) {
    }

    private final AuditLogRepository auditLogs;
    private final DeviceEventRepository deviceEvents;
    private final Clock clock;

    @Value("${audit.retention.audit-log-days:1825}")
    private int auditLogDays = AUDIT_LOG_MIN_DAYS;

    @Value("${audit.retention.device-event-days:1825}")
    private int deviceEventDays = 1825;

    @Autowired
    public AuditRetentionService(AuditLogRepository auditLogs, DeviceEventRepository deviceEvents) {
        this(auditLogs, deviceEvents, Clock.systemUTC());
    }

    AuditRetentionService(AuditLogRepository auditLogs, DeviceEventRepository deviceEvents, Clock clock) {
        this.auditLogs = auditLogs;
        this.deviceEvents = deviceEvents;
        this.clock = clock;
    }

    /** Prazo efetivo do audit_log, nunca abaixo do piso legal. */
    int effectiveAuditLogDays() {
        if (auditLogDays < AUDIT_LOG_MIN_DAYS) {
            log.warn("audit.retention.audit-log-days={} está abaixo do piso legal; usando {}", auditLogDays,
                    AUDIT_LOG_MIN_DAYS);
            return AUDIT_LOG_MIN_DAYS;
        }
        return auditLogDays;
    }

    @Transactional
    @Auditable(action = "AUDIT_RETENTION_PURGED", entity = "AuditLog",
            details = "'audit_log=' + #result.auditLogDeleted() + ',device_event=' + #result.deviceEventDeleted()")
    public Result purge() {
        Instant now = clock.instant();

        Instant auditCutoff = now.minus(Duration.ofDays(effectiveAuditLogDays()));
        LocalDateTime eventCutoff = LocalDateTime.ofInstant(now.minus(Duration.ofDays(deviceEventDays)),
                ZoneId.systemDefault());

        int audit = auditLogs.deleteOlderThan(auditCutoff);
        int events = deviceEvents.deleteOlderThan(eventCutoff);

        log.info("Expurgo por retenção concluído: audit_log={}, device_event={}", audit, events);
        return new Result(audit, events);
    }
}
