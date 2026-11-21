package com.n0hana.echoes_server.infra.logs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.n0hana.echoes_server.device.event.DeviceEventRepository;

class AuditRetentionServiceTests {

    private static final Instant NOW = Instant.parse("2026-11-21T12:00:00Z");

    private AuditLogRepository auditLogs;
    private DeviceEventRepository deviceEvents;
    private AuditRetentionService service;

    @BeforeEach
    void setUp() {
        auditLogs = mock(AuditLogRepository.class);
        deviceEvents = mock(DeviceEventRepository.class);
        service = new AuditRetentionService(auditLogs, deviceEvents, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("Expurga audit_log e device_event pelos prazos padrão (1825 dias)")
    void purgesWithDefaultWindows() {
        when(auditLogs.deleteOlderThan(any())).thenReturn(3);
        when(deviceEvents.deleteOlderThan(any())).thenReturn(7);

        AuditRetentionService.Result result = service.purge();

        assertEquals(3, result.auditLogDeleted());
        assertEquals(7, result.deviceEventDeleted());

        ArgumentCaptor<Instant> audit = ArgumentCaptor.forClass(Instant.class);
        verify(auditLogs).deleteOlderThan(audit.capture());
        assertEquals(NOW.minus(Duration.ofDays(1825)), audit.getValue());

        ArgumentCaptor<LocalDateTime> events = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(deviceEvents).deleteOlderThan(events.capture());
        assertEquals(LocalDateTime.ofInstant(NOW.minus(Duration.ofDays(1825)), ZoneId.systemDefault()),
                events.getValue());
    }

    @Test
    @DisplayName("device_event aceita prazo menor, configurável")
    void deviceEventWindowIsConfigurable() {
        ReflectionTestUtils.setField(service, "deviceEventDays", 365);

        service.purge();

        ArgumentCaptor<LocalDateTime> events = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(deviceEvents).deleteOlderThan(events.capture());
        assertEquals(LocalDateTime.ofInstant(NOW.minus(Duration.ofDays(365)), ZoneId.systemDefault()),
                events.getValue());
    }

    @Test
    @DisplayName("audit_log nunca é expurgado abaixo do piso legal, mesmo configurado menor")
    void auditLogWindowIsClampedToLegalFloor() {
        ReflectionTestUtils.setField(service, "auditLogDays", 30);

        service.purge();

        ArgumentCaptor<Instant> audit = ArgumentCaptor.forClass(Instant.class);
        verify(auditLogs).deleteOlderThan(audit.capture());
        assertEquals(NOW.minus(Duration.ofDays(AuditRetentionService.AUDIT_LOG_MIN_DAYS)), audit.getValue());
    }

    @Test
    @DisplayName("audit_log aceita prazo maior que o piso")
    void auditLogWindowCanBeLongerThanFloor() {
        ReflectionTestUtils.setField(service, "auditLogDays", 3650);

        service.purge();

        ArgumentCaptor<Instant> audit = ArgumentCaptor.forClass(Instant.class);
        verify(auditLogs).deleteOlderThan(audit.capture());
        assertEquals(NOW.minus(Duration.ofDays(3650)), audit.getValue());
    }

    @Test
    @DisplayName("Agendador desligado não expurga nada")
    void schedulerDisabledDoesNothing() {
        AuditRetentionService mocked = mock(AuditRetentionService.class);
        AuditRetentionScheduler scheduler = new AuditRetentionScheduler(mocked);
        ReflectionTestUtils.setField(scheduler, "enabled", false);

        scheduler.run();

        verify(mocked, never()).purge();
    }

    @Test
    @DisplayName("Falha no expurgo não derruba o agendador")
    void schedulerSurvivesFailure() {
        AuditRetentionService mocked = mock(AuditRetentionService.class);
        when(mocked.purge()).thenThrow(new IllegalStateException("gatilho recusou"));

        new AuditRetentionScheduler(mocked).run();

        verify(mocked).purge();
    }
}
