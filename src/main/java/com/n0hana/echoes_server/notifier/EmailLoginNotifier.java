package com.n0hana.echoes_server.notifier;

import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j 
@Service
@RequiredArgsConstructor
public class EmailLoginNotifier {
    private final JavaMailSender javaMailSender;

    @Value("${spring.mail.username}")
    private String origin;

    @Async
    public void send(String code, String destination) {
        try {
            MimeMessage message = javaMailSender.createMimeMessage();

            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    true,
                    StandardCharsets.UTF_8.name());

            String html = """
                    <html>
                        <body>
                            <h1>Echoes</h1>
                            <p>Código de verificação de dois fatores.</p>
                            <h2>%s</h2>
                            <p>
                                <strong>Lembre-se, o código é válido por 5 minutos</strong>
                            </p>
                        </body>
                    </html>
                    """.formatted(code);

            helper.setFrom(origin);
            helper.setTo(destination);
            helper.setSubject("Echoes - Código de Verificação");
            helper.setText(html, true);

            javaMailSender.send(message);
        } catch (Exception e) {
            log.error("Erro ao enviar e-mail de recuperação de senha: {}", e.getMessage());
        }
    }
}
