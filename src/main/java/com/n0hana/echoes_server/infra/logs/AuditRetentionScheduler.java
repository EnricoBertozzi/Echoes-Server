package com.n0hana.echoes_server.infra.logs;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuditRetentionScheduler {

    private final AuditRetentionService service;

    @Value("${audit.retention.enabled:true}")
    private boolean enabled = true;

    @Scheduled(cron = "${audit.retention.cron:0 30 3 * * *}")
    public void run() {
        if (!enabled)
            return;

        try {
            service.purge();
        } catch (RuntimeException e) {
            // ex.: gatilho do MySQL recusou, ou banco indisponível; tenta de novo no dia seguinte
            log.error("Falha no expurgo por retenção: {}", e.getClass().getSimpleName());
        }
    }
}
