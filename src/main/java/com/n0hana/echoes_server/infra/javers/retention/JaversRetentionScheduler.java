package com.n0hana.echoes_server.infra.javers.retention;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JaversRetentionScheduler {

    private final JaversRetentionService service;

    @Value("${javers.retention.enabled:true}")
    private boolean enabled = true;

    @Scheduled(cron = "${javers.retention.cron:0 0 3 * * *}")
    public void run() {
        if (!enabled)
            return;
        service.pruneAll();
    }
}
