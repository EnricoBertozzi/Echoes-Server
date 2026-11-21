package com.n0hana.echoes_server.user.event;

import java.time.Instant;
import java.util.UUID;

/** Publicado quando um usuário inativo volta a ficar ativo por rollback do histórico. */
public record UserRestoredEvent(UUID userId, Instant restoredAt) {
}
