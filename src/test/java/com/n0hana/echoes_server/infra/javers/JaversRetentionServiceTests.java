package com.n0hana.echoes_server.infra.javers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.n0hana.echoes_server.infra.javers.retention.JaversPruner;
import com.n0hana.echoes_server.infra.javers.retention.JaversRetentionPolicy;
import com.n0hana.echoes_server.infra.javers.retention.JaversRetentionPolicyRepository;
import com.n0hana.echoes_server.infra.javers.retention.JaversRetentionScheduler;
import com.n0hana.echoes_server.infra.javers.retention.JaversRetentionService;

class JaversRetentionServiceTests {

    private final JaversRetentionPolicyRepository policies = mock(JaversRetentionPolicyRepository.class);
    private final JaversPruner pruner = mock(JaversPruner.class);
    private final JaversRetentionService service = new JaversRetentionService(policies, pruner);

    private JaversRetentionPolicy policy(String type, int hot, int retention) {
        return JaversRetentionPolicy.builder().entityType(type).hotWindowDays(hot).retentionDays(retention)
                .legalBasis("x").active(true).updatedAt(Instant.now()).build();
    }

    private void assertDaysAgo(Instant actual, long days) {
        long diff = Math.abs(Duration.between(actual, Instant.now().minus(Duration.ofDays(days))).toSeconds());
        assertTrue(diff < 5, "esperado " + days + " dias atrás, diferença de " + diff + "s");
    }

    @Test
    @DisplayName("Usa retentionDays como corte e soma as remoções de todas as políticas")
    void usesRetentionCutoffAndSums() {
        ReflectionTestUtils.setField(service, "minSnapshotsPerEntity", 10);
        when(policies.findByActiveTrue()).thenReturn(List.of(policy("A", 90, 1825), policy("B", 90, 1825)));
        when(pruner.pruneEntity(eq("A"), any(), eq(10))).thenReturn(7);
        when(pruner.pruneEntity(eq("B"), any(), eq(10))).thenReturn(3);

        assertEquals(10, service.pruneAll());

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(pruner).pruneEntity(eq("A"), cutoff.capture(), eq(10));
        assertDaysAgo(cutoff.getValue(), 1825);
    }

    @Test
    @DisplayName("Se a retenção for menor que a janela quente, o corte respeita a janela quente")
    void hotWindowWinsOverShortRetention() {
        when(policies.findByActiveTrue()).thenReturn(List.of(policy("A", 90, 30)));

        service.pruneAll();

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(pruner).pruneEntity(eq("A"), cutoff.capture(), anyInt());
        assertDaysAgo(cutoff.getValue(), 90);
    }

    @Test
    @DisplayName("Falha em um tipo não impede a poda dos demais")
    void failureDoesNotStopOthers() {
        when(policies.findByActiveTrue()).thenReturn(List.of(policy("A", 90, 1825), policy("B", 90, 1825)));
        when(pruner.pruneEntity(eq("A"), any(), anyInt())).thenThrow(new IllegalStateException("boom"));
        when(pruner.pruneEntity(eq("B"), any(), anyInt())).thenReturn(4);

        assertEquals(4, service.pruneAll());
    }

    @Test
    @DisplayName("Scheduler desligado (javers.retention.enabled=false) não poda")
    void schedulerDisabledDoesNothing() {
        JaversRetentionService mocked = mock(JaversRetentionService.class);
        JaversRetentionScheduler scheduler = new JaversRetentionScheduler(mocked);

        ReflectionTestUtils.setField(scheduler, "enabled", false);
        scheduler.run();
        verify(mocked, never()).pruneAll();

        ReflectionTestUtils.setField(scheduler, "enabled", true);
        scheduler.run();
        verify(mocked).pruneAll();
    }
}
