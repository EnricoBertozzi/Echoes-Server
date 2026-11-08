package com.n0hana.echoes_server.infra.notifier.message;

import com.n0hana.echoes_server.infra.notifier.data.MessageData;

/**
 * Interface para definição de mensagem a serem enviadas em notificações
 * 
 * @param <T> Parâmetro genérico de {@link MessageData} contendo os dados para
 *            binding de conteúdo
 * 
 * @author Enrico Bertozzi
 */
public interface Message<T extends MessageData> {
    /**
     * Retorna um objeto para binding de dados
     * 
     * @return Objeto que estende {@link MessageData} com dados para envio da
     *         notificação
     */
    T getDate();
}
