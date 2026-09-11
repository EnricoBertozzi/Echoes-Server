package com.n0hana.echoes_server.notifier;

import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import com.n0hana.echoes_server.mfa.TwoFactorDTO;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "twofactor.provider", havingValue = "email")
public class EmailNotifier implements TwoFactorNotifier {

    private final JavaMailSender javaMailSender;

    @Value("${spring.mail.username}")
    private String origin;

    @Override
    public void send(TwoFactorDTO dto) {
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            
            MimeMessageHelper helper = new MimeMessageHelper(
                message,
                true,
                StandardCharsets.UTF_8.name()
            );

            String html = """
                <html>
                    <body>
                        <h1>Bem-vindo ao echoes!</h1>
                        <p>Para continuar o registro, clique <a href="http://localhost:5173/register?code=%s&email=%s">aqui</a></p>
                        <strong>Lembre-se, o link é válido por 5 minutos</strong>
                    </body>
                </html>
                """.formatted(dto.code(), dto.email());

            helper.setFrom(origin);
            helper.setTo(dto.email());
            helper.setSubject("Echoes Validation Code");
            helper.setText(html, true);

            javaMailSender.send(message);
        } catch (Exception e) {
            System.out.println("Erro ao enviar email: " + e.getMessage());
        }
    }
}
