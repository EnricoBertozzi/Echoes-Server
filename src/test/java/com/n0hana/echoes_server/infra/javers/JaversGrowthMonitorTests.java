package com.n0hana.echoes_server.infra.javers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import com.n0hana.echoes_server.infra.javers.monitor.JaversGrowthMonitor;
import com.n0hana.echoes_server.infra.javers.monitor.JaversStorageAlertService;

class JaversGrowthMonitorTests {

    private static final long MB = 1024L * 1024L;

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final JaversStorageAlertService alerts = mock(JaversStorageAlertService.class);
    private final io.micrometer.core.instrument.simple.SimpleMeterRegistry registry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
    private JaversGrowthMonitor monitor;

    @BeforeEach
    void setUp() {
        monitor = new JaversGrowthMonitor(jdbc, alerts, registry);
        ReflectionTestUtils.setField(monitor, "alertTo", "admin@test.local");
        ReflectionTestUtils.setField(monitor, "thresholdMb", 8192L);
    }

    private void measure(long count, long bytes) {
        when(jdbc.queryForObject(JaversGrowthMonitor.COUNT_SQL, Long.class)).thenReturn(count);
        when(jdbc.queryForObject(JaversGrowthMonitor.BYTES_SQL, Long.class)).thenReturn(bytes);
    }

    @Test
    @DisplayName("Acima do limite: alerta o administrador com os bytes medidos e guarda a medição")
    void alertsAboveThreshold() {
        measure(1_000_000, 9000 * MB);

        monitor.check();

        verify(alerts).alert("admin@test.local", 9000 * MB, 8192L);
        assertEquals(1_000_000, monitor.getSnapshotCount());
        assertEquals(9000 * MB, monitor.getSnapshotBytes());
    }

    @Test
    @DisplayName("Abaixo do limite não alerta")
    void doesNotAlertBelowThreshold() {
        measure(10, 100 * MB);

        monitor.check();

        verify(alerts, never()).alert(anyString(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("Não repete o alerta dentro do intervalo")
    void doesNotRepeatAlert() {
        measure(1, 9000 * MB);

        monitor.check();
        monitor.check();

        verify(alerts, times(1)).alert(anyString(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("Desligado, não consulta o banco nem alerta")
    void disabledDoesNothing() {
        ReflectionTestUtils.setField(monitor, "enabled", false);

        monitor.check();

        verify(jdbc, never()).queryForObject(anyString(), eq(Long.class));
        verify(alerts, never()).alert(anyString(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("Falha ao medir (ex.: banco sem information_schema.TABLES) não propaga e não alerta")
    void measurementFailureIsSwallowed() {
        when(jdbc.queryForObject(JaversGrowthMonitor.COUNT_SQL, Long.class)).thenThrow(new IllegalStateException("sem tabela"));

        monitor.check();

        verify(alerts, never()).alert(anyString(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("Sem destinatário configurado, registra mas não envia e-mail")
    void noRecipientNoEmail() {
        ReflectionTestUtils.setField(monitor, "alertTo", "");
        measure(1, 9000 * MB);

        monitor.check();

        verify(alerts, never()).alert(anyString(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("Registra os gauges javers.snapshot.count e javers.snapshot.bytes com os valores da última medição")
    void exposesGauges() {
        assertEquals(0.0, registry.get("javers.snapshot.count").gauge().value());

        measure(1234, 5 * MB);
        monitor.check();

        assertEquals(1234.0, registry.get("javers.snapshot.count").gauge().value());
        assertEquals(5.0 * MB, registry.get("javers.snapshot.bytes").gauge().value());
        assertEquals("Tamanho da tabela jv_snapshot em bytes", registry.get("javers.snapshot.bytes").gauge().getId().getDescription());
    }
}
