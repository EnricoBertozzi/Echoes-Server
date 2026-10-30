package com.n0hana.echoes_server.infra.notifier.message;

import com.n0hana.echoes_server.infra.notifier.data.RegisterInviteData;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Classe concreta que implementa {@link EmailMessage} para mensagem de
 * convite de novos usuários
 * 
 * @author Enrico Bertozzi
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class RegisterInviteMessage implements EmailMessage<RegisterInviteData> {
    private RegisterInviteData data;

    @Override
    public RegisterInviteData getDate() {
        return this.data;
    }

    @Override
    public String getTitle() {
        return "Echoes - Convite de Cadastro";
    }

    @Override
    public String getContent() {
        return """
            <html>
                <body>
                    <h1>Bem-vindo ao echoes!</h1>
                    <p>Para continuar o registro, clique <a href="http://localhost:5173/register?code=%s&email=%s">aqui</a></p>
                    <strong>Lembre-se, o link é válido por 5 minutos</strong>
                </body>
            </html>
            """
            .formatted(data.getCode(), data.getEmail());
    }

}
