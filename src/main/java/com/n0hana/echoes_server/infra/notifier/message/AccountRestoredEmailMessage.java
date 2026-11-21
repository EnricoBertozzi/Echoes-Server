package com.n0hana.echoes_server.infra.notifier.message;

import com.n0hana.echoes_server.infra.notifier.data.AccountRestoredData;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AccountRestoredEmailMessage implements EmailMessage<AccountRestoredData> {

    private AccountRestoredData data;

    @Override
    public AccountRestoredData getData() {
        return this.data;
    }

    @Override
    public String getTitle() {
        return "Echoes - Sua conta foi restaurada";
    }

    @Override
    public String getContent() {
        String name = data.getName() == null || data.getName().isBlank() ? "" : " " + data.getName();
        return """
                <html>
                    <body>
                        <h1>Echoes</h1>
                        <p>Olá%s, sua conta foi restaurada por um administrador.</p>
                        <p>Por segurança, suas sessões anteriores foram encerradas e você precisa entrar novamente.
                        Sua senha não foi alterada.</p>
                    </body>
                </html>
                """.formatted(name);
    }
}
