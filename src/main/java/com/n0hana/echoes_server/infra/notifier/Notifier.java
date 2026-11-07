package com.n0hana.echoes_server.infra.notifier;

import com.n0hana.echoes_server.infra.notifier.message.Message;

/**
 * Interface para implementação de canais de notificação
 * 
 * @author Enrico Bertozzi
 */
public interface Notifier<M extends Message<?>> {

    /**
     * Envia uma mensagem por um canal de notificação
     * 
     * @param origin  Origem da mensagem
     * @param to      Destinário da mensagem
     * @param message Mensagem a ser enviada
     */
    public void send(String to, M message);
}
