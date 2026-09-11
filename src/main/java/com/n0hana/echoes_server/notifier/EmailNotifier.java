package com.n0hana.echoes_server.notifier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import com.n0hana.echoes_server.mfa.TwoFactorDTO;

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
            SimpleMailMessage message = new SimpleMailMessage();
            
            message.setFrom(origin);
            message.setTo(dto.email());
            message.setSubject("Echoes Validation Code");
            message.setText(dto.code());

            javaMailSender.send(message);
        } catch (Exception e) {
            System.out.println("Erro ao enviar email: " + e.getMessage());
        }
    }
}
