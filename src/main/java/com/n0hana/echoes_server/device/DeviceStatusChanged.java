package com.n0hana.echoes_server.device;

import java.util.UUID;

/**
 * Evento publicado quando o status ou a instituição de um dispositivo muda.
 * Os ouvintes (aviso por MQTT, uso, dashboards) entram em etapas seguintes.
 */
public record DeviceStatusChanged(UUID deviceId, DeviceStatus status) {
}
