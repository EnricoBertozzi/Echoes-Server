package com.n0hana.echoes_server.infra.notifier.message;

import java.time.format.DateTimeFormatter;
import java.time.ZoneId;

import org.springframework.web.util.HtmlUtils;

import com.n0hana.echoes_server.infra.notifier.data.NewDeviceLoginData;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class NewDeviceLoginEmailMessage implements EmailMessage<NewDeviceLoginData> {
    private NewDeviceLoginData data;

    @Override
    public NewDeviceLoginData getData() {
        return this.data;
    }

    @Override
    public String getTitle() {
        return "Echoes - Novo acesso à sua conta";
    }

    @Override
    public String getContent() {
        String when = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
                .withZone(ZoneId.of("America/Sao_Paulo"))
                .format(data.getWhen());

        return """
            <html>
                <body>
                    <h1>Novo acesso à sua conta</h1>
                    <p>Sua conta foi acessada a partir de um dispositivo que não conhecemos.</p>
                    <ul>
                        <li>Quando: %s</li>
                        <li>Endereço IP: %s</li>
                        <li>Navegador: %s</li>
                    </ul>
                    <p>Se não foi você, troque sua senha imediatamente.</p>
                </body>
            </html>
            """.formatted(
                when,
                HtmlUtils.htmlEscape(String.valueOf(data.getIp())),
                HtmlUtils.htmlEscape(String.valueOf(data.getUserAgent())));
    }
}
