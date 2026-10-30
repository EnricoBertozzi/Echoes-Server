package com.n0hana.echoes_server.infra.notifier.message;

import com.n0hana.echoes_server.infra.notifier.data.InstitutionVerificationRejectedData;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Classe concreta que implementa {@link EmailMessage} para mensagem de
 * reijeição do cadastro de instituições
 * 
 * @author Enrico Bertozzi
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InstitutionVerificationRejectedEmailMessage implements EmailMessage<InstitutionVerificationRejectedData> {
    private InstitutionVerificationRejectedData data;

    @Override
    public InstitutionVerificationRejectedData getDate() {
        return this.data;
    }

    @Override
    public String getTitle() {
        return "Instituição %s rejeitada - CNPJ não encontrado".formatted(data.getName());
    }

    @Override
    public String getContent() {
        return """
                <html>
                    <body>
                        <h1>Verificação Rejeitada</h1>
                        <p>
                            A verificação da instituição <strong>%s</strong> (CNPJ: %s) falhou e foi rejeitada.</p>
                        <p>
                            Motivo: %s
                        </p>
                        <p>
                            <strong>Aviso:</strong> A instituição está inativa para compras e aguarda ação manual (excluir ou corrigir cadastro).
                        </p>
                    </body>
                </html>,
                """
                .formatted(data.getName(), data.getCnpj(), data.getReason());
    }

}
