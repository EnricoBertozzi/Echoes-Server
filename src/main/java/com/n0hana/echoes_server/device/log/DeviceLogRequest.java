package com.n0hana.echoes_server.device.log;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Lote enviado pelo ESP em {@code POST /api/v1/devices/me/logs}.
 *
 * <p>
 * {@code up} é o {@code millis()} do ESP (32 bits). O servidor não confia no
 * relógio do dispositivo: calcula quando cada evento ocorreu pela diferença
 * entre o {@code up} do lote e o {@code up} do evento.
 * </p>
 */
public record DeviceLogRequest(
    @NotNull @Pattern(regexp = "^[0-9a-f]{8}$") String bootId,
    @NotNull @PositiveOrZero @Max(4294967295L) Long up,
    @PositiveOrZero @Max(1000000) Integer dropped,
    @NotEmpty @Size(max = MAX_EVENTS) List<@Valid Event> events) {

  public static final int MAX_EVENTS = 50;

  public record Event(
      @NotNull @PositiveOrZero Long seq,
      @NotNull @PositiveOrZero @Max(4294967295L) Long up,
      @NotNull DeviceLogLevel lvl,
      @NotNull DeviceLogCategory cat,
      @NotBlank @Pattern(regexp = "^[A-Z0-9_]{1,40}$") String code,
      @NotNull @Size(max = 600) String msg,
      @PositiveOrZero @Max(65535) Integer n) {
  }
}
