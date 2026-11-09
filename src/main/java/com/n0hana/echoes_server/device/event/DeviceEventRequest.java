package com.n0hana.echoes_server.device.event;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/**
 * Corpo enviado pelo ESP em {@code POST /api/v1/devices/me/events}.
 * {@code scenarioId} é obrigatório em PLAY, PAUSE e STOP e proibido em
 * CONNECTION.
 */
public record DeviceEventRequest(@NotNull DeviceEventType type, UUID scenarioId) {
}
