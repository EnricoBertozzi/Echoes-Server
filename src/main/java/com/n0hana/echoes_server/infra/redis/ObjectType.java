package com.n0hana.echoes_server.infra.redis;

import java.time.Duration;

import lombok.Getter;

@Getter
public enum ObjectType implements RedisNamespace {

    PENDING_REGISTRATION(Duration.ofMinutes(15), "pending:registration:");

    private final Duration duration;
    private final String prefix;

    ObjectType(Duration duration, String prefix) {
        this.duration = duration;
        this.prefix = prefix;
    }
}
