package com.n0hana.echoes_server.infra.notifier;

import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import com.n0hana.echoes_server.infra.logs.AuditLog;
import com.n0hana.echoes_server.infra.logs.AuditLogService;
import com.n0hana.echoes_server.infra.notifier.message.EmailMessage;

import jakarta.mail.Address;
import jakarta.mail.SendFailedException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementação de {@link Notifier} para envio de notificações via E-mail.
 *
 * <p>
 * Cada envio gera um registro de auditoria com o resultado:
 * </p>
 * <ul>
 * <li>{@code SUCCESS}: o servidor SMTP aceitou a mensagem. Isso não garante a
 * entrega na caixa do destinatário (ver limitações no registro de riscos).</li>
 * <li>{@code FAILED} com "destinatário inexistente ou inválido": o endereço não
 * existe, tem formato inválido ou foi recusado pelo servidor SMTP.</li>
 * <li>{@code FAILED} com "falha de envio": erro de conexão, autenticação ou
 * timeout; a mensagem não saiu.</li>
 * </ul>
 *
 * @author Enrico Bertozzi
 */
@Service
@Slf4j
public class EmailNotifier implements Notifier<EmailMessage<?>> {

    static final String AUDIT_ACTION = "SEND";
    static final String AUDIT_ENTITY = "Email";

    static final String RESULT_SENT = "Enviado: aceito pelo servidor SMTP";
    static final String RESULT_NOT_FOUND = "Destinatário inexistente ou inválido";
    static final String RESULT_FAILED = "Falha de envio";

    @Value("${spring.mail.username}")
    private String origin;

    private final JavaMailSender javaMailSender;
    private final AuditLogService auditLogService;

    public EmailNotifier(JavaMailSender javaMailSender, AuditLogService auditLogService) {
        this.javaMailSender = javaMailSender;
        this.auditLogService = auditLogService;
    }

    @Override
    public void send(String to, EmailMessage<?> message) {
        String result;

        try {
            // validação estrita: sem ela, "nao-e-email" passa e só o SMTP rejeitaria
            new InternetAddress(to, true).validate();

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
            result = RESULT_SENT;
        } catch (Exception e) {
            result = isRecipientProblem(e) ? RESULT_NOT_FOUND : RESULT_FAILED;
            // registra a causa, sem o destinatário (dado pessoal)
            log.error("Falha ao enviar e-mail '{}': {}", message.getTitle(), e.getMessage(), e);
        }

        audit(to, message, result);
    }

    /** o servidor recusou o endereço ou ele nem é válido */
    static boolean isRecipientProblem(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof AddressException)
                return true;
            if (t instanceof SendFailedException sfe && hasInvalid(sfe.getInvalidAddresses()))
                return true;
            if (t.getCause() == t)
                break;
        }

        if (error instanceof MailSendException mse && mse.getFailedMessages() != null) {
            for (Exception failure : mse.getFailedMessages().values()) {
                if (isRecipientProblem(failure))
                    return true;
            }
        }
        return false;
    }

    private static boolean hasInvalid(Address[] invalid) {
        return invalid != null && invalid.length > 0;
    }

    private void audit(String to, EmailMessage<?> message, String result) {
        boolean sent = RESULT_SENT.equals(result);

        AuditLog entry = AuditLog.builder()
                .action(AUDIT_ACTION)
                .entity(AUDIT_ENTITY)
                .status(sent ? "SUCCESS" : "FAILED")
                .details(result + " | tipo=" + message.getClass().getSimpleName()
                        + " | destinatário=" + mask(to))
                .build();

        auditLogService.recordEmailOutcome(entry);
    }

    /** d***@dominio.com: identifica o envio sem gravar o endereço completo */
    static String mask(String email) {
        if (email == null)
            return "(nulo)";
        int at = email.indexOf('@');
        if (at <= 0)
            return "***";
        return email.charAt(0) + "***" + email.substring(at);
    }
}
