package com.n0hana.echoes_server.notifier;

import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
                    StandardCharsets.UTF_8.name());

            // Formata o Instant para HH:mm no fuso horário adequado
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm")
                    .withZone(ZoneId.of("America/Sao_Paulo"));
            String horaExpiracao = formatter.format(dto.expiresAt());

            String html = """
                    <html>
                        <body>
                            <h1>Echoes - Código de Acesso</h1>
                            <p>O seu código de acesso é <strong>%s</strong> e é válido até às <strong>%s</strong>.</p>
                        </body>
                    </html>
                    """.formatted(dto.code(), horaExpiracao);

            helper.setFrom(origin);
            helper.setTo(dto.email());
            helper.setSubject("Echoes - Código de Validação");
            helper.setText(html, true);

            javaMailSender.send(message);
        } catch (Exception e) {
            System.out.println("Erro ao enviar email: " + e.getMessage());
        }
    }
}
