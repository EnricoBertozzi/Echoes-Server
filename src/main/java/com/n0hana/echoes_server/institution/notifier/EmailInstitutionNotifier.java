package com.n0hana.echoes_server.institution.notifier;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Pageable;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.model.Admin;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "institution.notifier", havingValue = "email", matchIfMissing = true)
public class EmailInstitutionNotifier implements InstitutionNotifier {

    private final JavaMailSender javaMailSender;
    private final UserRepository userRepository;

    @Value("${spring.mail.username}")
    private String origin;

    @Value("${institution.alert-email:}")
    private String alertEmailFallback;

    @Async
    @Override
    public void notifyPendingVerification(InstitutionNotificationData data, String reason) {
        String subject = "Instituição " + data.name() + " pendente de verificação";
        String html = String.format(
                "<html><body>" +
                "<h1>Verificação Pendente</h1>" +
                "<p>A instituição <strong>%s</strong> (CNPJ: %s) foi cadastrada, mas a validação do CNPJ está pendente.</p>" +
                "<p>Motivo: %s</p>" +
                "<p><strong>Aviso:</strong> A instituição está bloqueada para compras de dispositivos e cenários até que a verificação seja concluída pelo sistema.</p>" +
                "</body></html>",
                data.name(), data.cnpj(), reason);
        sendEmail(resolveRecipients(), subject, html);
    }

    @Async
    @Override
    public void notifyVerificationCompleted(InstitutionNotificationData data) {
        String subject = "Instituição " + data.name() + " verificada com sucesso";
        String html = String.format(
                "<html><body>" +
                "<h1>Verificação Concluída</h1>" +
                "<p>A instituição <strong>%s</strong> (CNPJ: %s) teve seu CNPJ verificado com sucesso na Receita Federal.</p>" +
                "<p>Os dados foram atualizados e o bloqueio para compras foi removido.</p>" +
                "</body></html>",
                data.name(), data.cnpj());
        sendEmail(resolveRecipients(), subject, html);
    }

    @Async
    @Override
    public void notifyVerificationRejected(InstitutionNotificationData data, String reason) {
        String subject = "Instituição " + data.name() + " rejeitada — CNPJ não encontrado";
        String html = String.format(
                "<html><body>" +
                "<h1>Verificação Rejeitada</h1>" +
                "<p>A verificação da instituição <strong>%s</strong> (CNPJ: %s) falhou e foi rejeitada.</p>" +
                "<p>Motivo: %s</p>" +
                "<p><strong>Aviso:</strong> A instituição está inativa para compras e aguarda ação manual (excluir ou corrigir cadastro).</p>" +
                "</body></html>",
                data.name(), data.cnpj(), reason);
        sendEmail(resolveRecipients(), subject, html);
    }

    private List<String> resolveRecipients() {
        List<String> recipients = new ArrayList<>();
        List<Admin> admins = userRepository.findAdmins(Pageable.unpaged()).getContent();
        for (Admin admin : admins) {
            if (admin.isActive() && admin.getEmail() != null) {
                recipients.add(admin.getEmail());
            }
        }
        if (alertEmailFallback != null && !alertEmailFallback.isBlank()) {
            recipients.add(alertEmailFallback);
        }
        return recipients;
    }

    private void sendEmail(List<String> recipients, String subject, String htmlBody) {
        if (recipients.isEmpty()) {
            log.warn("Nenhum destinatário encontrado para envio de e-mail de notificação de instituição.");
            return;
        }
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(origin);
            helper.setTo(recipients.toArray(new String[0]));
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            javaMailSender.send(message);
        } catch (Exception e) {
            log.error("Erro ao enviar email de notificação de instituição: {}", e.getMessage());
        }
    }
}
