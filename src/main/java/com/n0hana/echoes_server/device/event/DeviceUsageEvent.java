package com.n0hana.echoes_server.device.event;

import java.time.LocalDateTime;
import java.util.UUID;

/** Evento de uso já validado, pronto para ser gravado. */
public record DeviceUsageEvent(
    UUID deviceId,
    UUID institutionId,
    UUID scenarioId,
    DeviceEventType type,
    LocalDateTime occurredAt) {
}
