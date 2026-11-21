package com.n0hana.echoes_server.infra.notifier.message;

import com.n0hana.echoes_server.infra.notifier.data.JaversStorageAlertData;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class JaversStorageAlertEmailMessage implements EmailMessage<JaversStorageAlertData> {

    private JaversStorageAlertData data;

    @Override
    public JaversStorageAlertData getData() {
        return this.data;
    }

    @Override
    public String getTitle() {
        return "Echoes - Histórico do Javers acima do limite";
    }

    @Override
    public String getContent() {
        long mb = data.getBytes() / (1024 * 1024);
        return """
                <html>
                    <body>
                        <h1>Echoes</h1>
                        <p>A tabela de snapshots do Javers está com %d MB, acima do limite de %d MB.</p>
                        <p>Nenhuma poda foi disparada automaticamente. Revise as políticas de retenção
                        ou execute a poda manual.</p>
                    </body>
                </html>
                """.formatted(mb, data.getThresholdMb());
    }
}
