package com.n0hana.echoes_server.device.log;

import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Apaga os logs de dispositivo mais velhos que o prazo de retenção (7 dias por padrão). */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceLogRetentionScheduler {

  private final DeviceLogRepository repository;

  @Value("${device-log.retention-days:7}")
  private int retentionDays = 7;

  @Value("${device-log.retention.enabled:true}")
  private boolean enabled = true;

  @Scheduled(cron = "${device-log.retention.cron:0 */30 * * * *}")
  public void purge() {
    if (!enabled)
      return;
    try {
      int deleted = repository.deleteOlderThan(Instant.now().minus(Duration.ofDays(retentionDays)));
      if (deleted > 0)
        log.info("Expurgo de logs de dispositivo: {} removido(s)", deleted);
    } catch (RuntimeException e) {
      log.error("Falha no expurgo de logs de dispositivo: {}", e.getClass().getSimpleName());
    }
  }
}
