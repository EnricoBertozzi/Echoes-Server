package com.n0hana.echoes_server.device.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class DeviceLogIngestServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-23T12:00:00Z");

  @Test
  void eventAgeIsBatchUptimeMinusEventUptime() {
    assertEquals(NOW.minusMillis(5_000), DeviceLogIngestService.occurredAt(NOW, 65_000, 60_000));
  }

  @Test
  void uptimeWrapAroundIsHandled() {
    // millis() de 32 bits deu a volta entre o evento e o envio: idade real = 2 s
    assertEquals(NOW.minusMillis(2_000), DeviceLogIngestService.occurredAt(NOW, 1_000, 4_294_966_296L));
  }

  @Test
  void implausiblyOldEventFallsBackToNow() {
    assertEquals(NOW, DeviceLogIngestService.occurredAt(NOW, 4_000_000_000L, 0));
  }

  @Test
  void sanitizeStripsControlCharactersAndTruncates() {
    String cleaned = DeviceLogIngestService.sanitize("linha1\nFAKE 2026 ERROR\r\tfim");
    assertFalse(cleaned.contains("\n"));
    assertFalse(cleaned.contains("\r"));
    assertEquals(DeviceLogIngestService.MAX_MESSAGE, DeviceLogIngestService.sanitize("x".repeat(900)).length());
    assertEquals("-", DeviceLogIngestService.sanitize("  \n "));
  }
}
