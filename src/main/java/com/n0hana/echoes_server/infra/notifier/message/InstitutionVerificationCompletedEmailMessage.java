package com.n0hana.echoes_server.infra.notifier.message;

import com.n0hana.echoes_server.infra.notifier.data.InstituitionVerificationCompletedData;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Classe concreta que implementa {@link EmailMessage} para mensagem de
 * confirmação de cadastro de instituições
 * 
 * @author Enrico Bertozzi
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class InstitutionVerificationCompletedEmailMessage
        implements EmailMessage<InstituitionVerificationCompletedData> {
    private InstituitionVerificationCompletedData data;

    @Override
    public InstituitionVerificationCompletedData getDate() {
        return this.data;
    }

    @Override
    public String getTitle() {
        return "Echoes - Instituição %s Pendente de Verificação".formatted(data.getName());
    }

    @Override
    public String getContent() {
        return """
                <html>
                    <body>
                        <h1>Verificação Concluída</h1>
                        <p>
                            A instituição <strong>%s</strong> (CNPJ: %s) teve seu CNPJ verificado com sucesso na Receita Federal.
                        </p>
                        <p>
                            Os dados foram atualizados e o bloqueio para compras foi removido.
                        </p>
                    </body>
                </html>
                """
                .formatted(data.getName(), data.getCnpj());
    }

}
