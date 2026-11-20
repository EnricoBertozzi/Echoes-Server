package com.n0hana.echoes_server.infra.notifier.data;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** dados do alerta de login em dispositivo novo (RF11) */
@Getter
@AllArgsConstructor
public class NewDeviceLoginData implements MessageData {
    private String ip;
    private String userAgent;
    private Instant when;
}
