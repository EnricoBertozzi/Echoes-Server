package com.n0hana.echoes_server.infra.notifier.data;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Dados da notificação de conta restaurada por rollback administrativo. */
@Getter
@AllArgsConstructor
public class AccountRestoredData implements MessageData {
    private String name;
}
