package com.n0hana.echoes_server.infra.javers.monitor;

import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * Acompanha o crescimento da tabela de snapshots e alerta quando passa do limite. Não dispara poda:
 * a decisão de apagar histórico continua humana.
 */
@Slf4j
@Component
public class JaversGrowthMonitor {

    public static final String COUNT_SQL = "SELECT COUNT(*) FROM jv_snapshot";
    public static final String BYTES_SQL = "SELECT data_length + index_length FROM information_schema.TABLES "
            + "WHERE table_schema = DATABASE() AND table_name = 'jv_snapshot'";

    private final JdbcTemplate jdbc;
    private final JaversStorageAlertService alerts;

    @Value("${javers.monitor.enabled:true}")
    private boolean enabled = true;

    @Value("${javers.monitor.snapshot-bytes-threshold-mb:8192}")
    private long thresholdMb = 8192;

    @Value("${javers.monitor.alert-to:${api.security.admin.email:}}")
    private String alertTo = "";

    /** evita repetir o mesmo alerta a cada execução horária */
    @Value("${javers.monitor.alert-interval-hours:24}")
    private long alertIntervalHours = 24;

    private volatile long snapshotCount;
    private volatile long snapshotBytes;
    private volatile Instant lastAlert;

    public JaversGrowthMonitor(JdbcTemplate jdbc, JaversStorageAlertService alerts, MeterRegistry meterRegistry) {
        this.jdbc = jdbc;
        this.alerts = alerts;

        Gauge.builder("javers.snapshot.count", () -> snapshotCount)
                .description("Número de snapshots no Javers")
                .register(meterRegistry);
        Gauge.builder("javers.snapshot.bytes", () -> snapshotBytes)
                .description("Tamanho da tabela jv_snapshot em bytes")
                .baseUnit("bytes")
                .register(meterRegistry);
    }

    @Scheduled(cron = "${javers.monitor.cron:0 0 * * * *}")
    public void check() {
        if (!enabled)
            return;

        try {
            snapshotCount = nullToZero(jdbc.queryForObject(COUNT_SQL, Long.class));
            snapshotBytes = nullToZero(jdbc.queryForObject(BYTES_SQL, Long.class));
        } catch (RuntimeException e) {
            log.warn("Monitor do Javers não conseguiu medir o histórico: {}", e.getMessage());
            return;
        }

        log.info("Javers: snapshots={} bytes={}", snapshotCount, snapshotBytes);

        if (snapshotBytes > thresholdMb * 1024 * 1024 && shouldAlert()) {
            lastAlert = Instant.now();
            log.warn("Javers acima do limite de {} MB: {} bytes", thresholdMb, snapshotBytes);
            if (alertTo != null && !alertTo.isBlank())
                alerts.alert(alertTo, snapshotBytes, thresholdMb);
        }
    }

    private boolean shouldAlert() {
        return lastAlert == null || Duration.between(lastAlert, Instant.now()).toHours() >= alertIntervalHours;
    }

    private static long nullToZero(Long value) {
        return value == null ? 0 : value;
    }

    /** valores da última medição (também expostos como gauges) */
    public long getSnapshotCount() {
        return snapshotCount;
    }

    public long getSnapshotBytes() {
        return snapshotBytes;
    }
}
