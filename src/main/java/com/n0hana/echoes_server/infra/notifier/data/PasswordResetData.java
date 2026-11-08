package com.n0hana.echoes_server.infra.notifier.data;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Classe que implementa {@link MessageData} para notificação de redefinição de
 * senhas do usuário
 * 
 * @author Enrico Bertozzi
 */
@Getter
@AllArgsConstructor
public class PasswordResetData implements MessageData {
    private String code;
}
