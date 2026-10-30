package com.n0hana.echoes_server.infra.notifier.data;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Classe que implementa {@link MessageData} para notificação de verificação de
 * dois fatores durante o login
 * 
 * @author Enrico Bertozzi
 */
@Getter
@AllArgsConstructor
public class MfaCodeData implements MessageData {
    private String code;
}
