package com.n0hana.echoes_server.infra.notifier.data;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Classe que implementa {@link MessageData} para notificação de cadastro de
 * novo usuário
 * 
 * @author Enrico Bertozzi
 */
@Getter
@AllArgsConstructor
public class RegisterInviteData implements MessageData {
    private String code;

    private String email;
}
