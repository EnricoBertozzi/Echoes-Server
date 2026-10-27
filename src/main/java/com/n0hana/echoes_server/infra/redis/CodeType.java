package com.n0hana.echoes_server.infra.redis;

import java.time.Duration;

import lombok.Getter;

@Getter
public enum CodeType implements RedisNamespace {

    MFA(Duration.ofMinutes(15), "pending:login:"),
    PASSWORD_RESET(Duration.ofMinutes(15), "password:code:"),
    PASSWORD_TOKEN(Duration.ofMinutes(15), "password:token:"),
    INVITE(Duration.ofMinutes(15), "registration:"),

    // TTL customizado na chamada (calculado a partir da expiração do JWT)
    JWT_BLACKLIST(Duration.ofHours(2), "blacklist:token:");

    private final Duration duration;
    private final String prefix;

    CodeType(Duration duration, String prefix) {
        this.duration = duration;
        this.prefix = prefix;
    }
}
