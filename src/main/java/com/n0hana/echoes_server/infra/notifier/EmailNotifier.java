package com.n0hana.echoes_server.infra.notifier;

import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import com.n0hana.echoes_server.infra.notifier.message.EmailMessage;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementação de {@link Notifier} para envio de notificações via E-mail
 * 
 * @author Enrico Bertozzi
 */
@Service
@Slf4j
public class EmailNotifier implements Notifier<EmailMessage<?>> {

    @Value("${spring.mail.username}")
    private String origin;

    private final JavaMailSender javaMailSender;

    public EmailNotifier(JavaMailSender javaMailSender) {
        this.javaMailSender = javaMailSender;

    }

    @Override
    public void send(String to, EmailMessage<?> message) {
        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();

            MimeMessageHelper helper = new MimeMessageHelper(
                    mimeMessage,
                    false,
                    StandardCharsets.UTF_8.name());

            helper.setFrom(origin);
            helper.setTo(to);
            helper.setSubject(message.getTitle());
            helper.setText(message.getContent(), true);

            javaMailSender.send(mimeMessage);
        } catch (Exception e) {
            log.info("Erro ao enviar email: " + e.getMessage());
        }
    }
}
