package com.n0hana.echoes_server.infra.redis;

import java.time.Duration;

import lombok.Getter;

@Getter
public enum CodeType implements RedisNamespace {

    MFA(Duration.ofMinutes(5), "pending:login:"),
    PASSWORD_RESET(Duration.ofMinutes(5), "password:code:"),
    PASSWORD_TOKEN(Duration.ofMinutes(5), "password:token:"),
    INVITE(Duration.ofDays(7), "registration:"),

    // código de entrada da turma (código para turma e turma para código)
    CLASSROOM_JOIN(Duration.ofMinutes(15), "classroom:join:code:"),
    CLASSROOM_JOIN_BY_ID(Duration.ofMinutes(15), "classroom:join:id:"),

    // contadores de tentativa (a janela abre na primeira falha)
    LOGIN_ATTEMPTS(Duration.ofMinutes(15), "attempts:login:"),
    MFA_ATTEMPTS(Duration.ofMinutes(15), "attempts:mfa:"),
    PASSWORD_VALIDATE_ATTEMPTS(Duration.ofMinutes(15), "attempts:password-validate:"),
    PASSWORD_FORGOT_ATTEMPTS(Duration.ofMinutes(15), "attempts:password-forgot:"),
    CLASSROOM_JOIN_ATTEMPTS(Duration.ofMinutes(15), "attempts:classroom-join:"),

    KNOWN_DEVICE(Duration.ofDays(180), "known-device:"),
    KNOWN_DEVICE_USER(Duration.ofDays(180), "known-device-user:"),

    // TTL customizado na chamada (calculado a partir da expiração do JWT)
    JWT_BLACKLIST(Duration.ofHours(2), "blacklist:token:"),

    // corte de revogação dos tokens de um dispositivo: vida máxima do token (5 min) + tolerância de relógio
    DEVICE_TOKENS_REVOKED(Duration.ofMinutes(6), "device:tokens-revoked-at:");

    private final Duration duration;
    private final String prefix;

    CodeType(Duration duration, String prefix) {
        this.duration = duration;
        this.prefix = prefix;
    }
}
