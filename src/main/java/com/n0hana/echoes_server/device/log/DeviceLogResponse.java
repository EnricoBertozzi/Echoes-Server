package com.n0hana.echoes_server.device.log;

import java.time.Instant;
import java.util.UUID;

public record DeviceLogResponse(
    UUID id,
    Instant occurredAt,
    DeviceLogLevel level,
    DeviceLogCategory category,
    String code,
    String message,
    int occurrences,
    String bootId) {

  public static DeviceLogResponse from(DeviceLogModel log) {
    return new DeviceLogResponse(log.getId(), log.getOccurredAt(), log.getLevel(), log.getCategory(),
        log.getCode(), log.getMessage(), log.getOccurrences(), log.getBootId());
  }
}
