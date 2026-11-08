package com.n0hana.echoes_server.infra.notifier.message;

import com.n0hana.echoes_server.infra.notifier.data.MessageData;

/**
 * Interface para implementação de {@link Message} para notificações por email.
 * 
 * @param <T> Parâmetro genérico de {@link MessageData} contendo os dados para
 *            binding de conteúdo
 * 
 * @author Enrico Bertozzi
 */
public interface EmailMessage<T extends MessageData> extends Message<T> {
    /**
     * Retorna o titulo do email.
     * 
     * @return {@link String} Contendo o titulo do email
     */
    String getTitle();

    /**
     * Retorna o contéudo do email com dados com dados atribuídos.
     * 
     * @return Conteúdo do email formatado
     */
    String getContent();
}
