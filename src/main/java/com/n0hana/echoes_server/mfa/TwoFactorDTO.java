package com.n0hana.echoes_server.mfa;

import java.time.Instant;

public record TwoFactorDTO(
    String email,
    String code,
    Instant expiresAt
    ) {}
