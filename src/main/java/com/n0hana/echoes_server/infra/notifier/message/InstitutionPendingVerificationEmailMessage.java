package com.n0hana.echoes_server.infra.notifier.message;

import com.n0hana.echoes_server.infra.notifier.data.InstitutionPendingVerificationData;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Classe concreta que implementa {@link EmailMessage} para mensagem de
 * verificação de cadastro de instituições
 * 
 * @author Enrico Bertozzi
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class InstitutionPendingVerificationEmailMessage implements EmailMessage<InstitutionPendingVerificationData> {
    private InstitutionPendingVerificationData data;

    @Override
    public InstitutionPendingVerificationData getDate() {
        return this.data;
    }

    @Override
    public String getTitle() {
        return "Instituição %s pendente de verificação".formatted(data.getName());
    }

    @Override
    public String getContent() {
        return """
                <html>
                    <body>
                        <h1>Verificação Pendente</h1>
                        <p>
                            A instituição <strong>%s</strong> (CNPJ: %s) foi cadastrada, mas a validação do CNPJ está pendente.
                        </p>
                        <p>
                            Motivo: %s
                        </p>
                        <p>
                            <strong>Aviso:</strong> A instituição está bloqueada para compras de dispositivos e cenários até que a verificação seja concluída pelo sistema.
                        </p>
                    </body>
                </html>
                """
                .formatted(data.getName(), data.getCnpj(), data.getReason());

    }

}
