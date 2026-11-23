package com.n0hana.echoes_server.device.log;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("Expurgo de logs de dispositivo")
class DeviceLogRetentionSchedulerTests {

  private final DeviceLogRepository repository = mock(DeviceLogRepository.class);
  private final DeviceLogRetentionScheduler scheduler = new DeviceLogRetentionScheduler(repository);

  @Test
  @DisplayName("apaga logs mais velhos que 7 dias")
  void deletesOlderThanRetention() {
    when(repository.deleteOlderThan(any(Instant.class))).thenReturn(12);

    scheduler.purge();

    ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
    verify(repository).deleteOlderThan(cutoff.capture());
    Duration age = Duration.between(cutoff.getValue(), Instant.now());
    assertTrue(age.compareTo(Duration.ofDays(7)) >= 0 && age.compareTo(Duration.ofDays(7).plusMinutes(1)) < 0);
  }

  @Test
  @DisplayName("respeita o prazo configurado")
  void usesConfiguredRetention() {
    ReflectionTestUtils.setField(scheduler, "retentionDays", 2);

    scheduler.purge();

    ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
    verify(repository).deleteOlderThan(cutoff.capture());
    assertTrue(Duration.between(cutoff.getValue(), Instant.now()).compareTo(Duration.ofDays(3)) < 0);
  }

  @Test
  @DisplayName("desligado não toca no banco")
  void disabledDoesNothing() {
    ReflectionTestUtils.setField(scheduler, "enabled", false);

    scheduler.purge();

    verifyNoInteractions(repository);
  }

  @Test
  @DisplayName("falha do banco não derruba o agendador")
  void swallowsRepositoryFailure() {
    when(repository.deleteOlderThan(any(Instant.class))).thenThrow(new IllegalStateException("banco fora"));

    assertDoesNotThrow(scheduler::purge);
  }
}
