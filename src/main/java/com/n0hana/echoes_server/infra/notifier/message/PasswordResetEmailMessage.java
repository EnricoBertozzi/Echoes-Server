package com.n0hana.echoes_server.infra.notifier.message;

import com.n0hana.echoes_server.infra.notifier.data.PasswordResetData;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Classe concreta que implementa {@link EmailMessage} para mensagem de
 * redefinição de senha
 * 
 * @author Enrico Bertozzi
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PasswordResetEmailMessage implements EmailMessage<PasswordResetData> {
    private PasswordResetData data;

    @Override
    public PasswordResetData getDate() {
        return this.data;
    }

    @Override
    public String getTitle() {
        return "Echoes - Código de Redefinição de Senha";
    }

    @Override
    public String getContent() {
        return """
                <html>
                    <body>
                        <h1>Echoes</h1>
                        <p>Código de verificação para re-definição de senha.</p>
                        <h2>%s</h2>
                        <p>
                            <strong>Lembre-se, o código é válido por 5 minutos</strong>
                        </p>
                    </body>
                </html>
                """.formatted(data.getCode());
    }

}
