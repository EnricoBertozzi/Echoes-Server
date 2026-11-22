package com.n0hana.echoes_server.device.log;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

@DisplayName("Validação do lote de logs enviado pelo ESP")
class DeviceLogRequestValidationTests {

  private static ValidatorFactory factory;
  private static Validator validator;

  @BeforeAll
  static void init() {
    factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @AfterAll
  static void close() {
    factory.close();
  }

  private static DeviceLogRequest.Event event() {
    return new DeviceLogRequest.Event(1L, 1000L, DeviceLogLevel.INFO, DeviceLogCategory.WIFI, "WIFI_CONNECTED", "ok",
        1);
  }

  private static DeviceLogRequest request(String bootId, Long up, Integer dropped, List<DeviceLogRequest.Event> events) {
    return new DeviceLogRequest(bootId, up, dropped, events);
  }

  private static boolean violates(Set<? extends ConstraintViolation<?>> violations, String path) {
    return violations.stream().anyMatch(v -> v.getPropertyPath().toString().contains(path));
  }

  @Test
  @DisplayName("lote válido não tem violações")
  void validBatch() {
    assertTrue(validator.validate(request("a1b2c3d4", 5000L, 0, List.of(event()))).isEmpty());
  }

  @Test
  @DisplayName("bootId precisa ter 8 caracteres hexadecimais minúsculos")
  void invalidBootId() {
    assertTrue(violates(validator.validate(request("XYZ", 5000L, 0, List.of(event()))), "bootId"));
    assertTrue(violates(validator.validate(request(null, 5000L, 0, List.of(event()))), "bootId"));
  }

  @Test
  @DisplayName("up precisa caber em 32 bits sem sinal")
  void invalidUptime() {
    assertTrue(violates(validator.validate(request("a1b2c3d4", 4294967296L, 0, List.of(event()))), "up"));
    assertTrue(violates(validator.validate(request("a1b2c3d4", -1L, 0, List.of(event()))), "up"));
    assertTrue(violates(validator.validate(request("a1b2c3d4", null, 0, List.of(event()))), "up"));
  }

  @Test
  @DisplayName("dropped não pode ser negativo nem absurdo")
  void invalidDropped() {
    assertTrue(violates(validator.validate(request("a1b2c3d4", 1L, -1, List.of(event()))), "dropped"));
    assertTrue(violates(validator.validate(request("a1b2c3d4", 1L, 2_000_000, List.of(event()))), "dropped"));
  }

  @Test
  @DisplayName("exige de 1 a 50 eventos")
  void eventCountLimits() {
    assertTrue(violates(validator.validate(request("a1b2c3d4", 1L, 0, List.of())), "events"));

    List<DeviceLogRequest.Event> tooMany = new ArrayList<>();
    for (int i = 0; i <= DeviceLogRequest.MAX_EVENTS; i++)
      tooMany.add(event());
    assertTrue(violates(validator.validate(request("a1b2c3d4", 1L, 0, tooMany)), "events"));
  }

  @Test
  @DisplayName("valida os campos de cada evento")
  void invalidEventFields() {
    var bad = new DeviceLogRequest.Event(-1L, 4294967296L, null, null, "minusculo", "x".repeat(601), 70000);
    var violations = validator.validate(request("a1b2c3d4", 1L, 0, List.of(bad)));

    for (String field : List.of("seq", "up", "lvl", "cat", "code", "msg", "n"))
      assertTrue(violates(violations, "events[0]." + field), "esperava violação em " + field);
  }

  @Test
  @DisplayName("code vazio é recusado")
  void blankCode() {
    var bad = new DeviceLogRequest.Event(1L, 1L, DeviceLogLevel.INFO, DeviceLogCategory.WIFI, " ", "ok", 1);
    assertTrue(violates(validator.validate(request("a1b2c3d4", 1L, 0, List.of(bad))), "events[0].code"));
  }
}
