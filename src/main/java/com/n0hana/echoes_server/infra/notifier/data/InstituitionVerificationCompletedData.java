package com.n0hana.echoes_server.infra.notifier.data;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Classe que implementa {@link MessageData} para notificação verificação de
 * instituição completa.
 * 
 * @author Enrico Bertozzi
 */
@Getter
@AllArgsConstructor
public class InstituitionVerificationCompletedData implements MessageData {
    private String name;

    private String cnpj;
}
