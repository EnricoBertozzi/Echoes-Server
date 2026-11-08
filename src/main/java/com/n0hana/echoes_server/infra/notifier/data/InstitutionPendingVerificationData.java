package com.n0hana.echoes_server.infra.notifier.data;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Classe que implementa {@link MessageData} para notificação de verificação de
 * de cadastro da instituição
 * 
 * @author Enrico Bertozzi
 */
@Getter
@AllArgsConstructor
public class InstitutionPendingVerificationData implements MessageData {
    private String reason;
    private String name;
    private String cnpj;
}
