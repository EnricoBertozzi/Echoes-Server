package com.n0hana.echoes_server.auth.attempt;

import java.time.Duration;

import com.n0hana.echoes_server.infra.redis.CodeType;

import lombok.Getter;

/** o que é contado e quanto aguenta antes de bloquear */
@Getter
public enum AttemptScope {

    LOGIN(CodeType.LOGIN_ATTEMPTS, 5),
    MFA(CodeType.MFA_ATTEMPTS, 5),
    PASSWORD_VALIDATE(CodeType.PASSWORD_VALIDATE_ATTEMPTS, 5),
    PASSWORD_FORGOT(CodeType.PASSWORD_FORGOT_ATTEMPTS, 3);

    private final CodeType type;
    private final int max;

    AttemptScope(CodeType type, int max) {
        this.type = type;
        this.max = max;
    }

    public Duration window() {
        return type.getDuration();
    }
}
