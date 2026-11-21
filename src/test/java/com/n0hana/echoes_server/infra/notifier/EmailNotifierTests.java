package com.n0hana.echoes_server.infra.notifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import com.n0hana.echoes_server.infra.logs.AuditLog;
import com.n0hana.echoes_server.infra.logs.AuditLogService;
import com.n0hana.echoes_server.infra.notifier.data.MfaCodeData;
import com.n0hana.echoes_server.infra.notifier.message.MfaCodeEmailMessage;

import jakarta.mail.Session;
import jakarta.mail.SendFailedException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

class EmailNotifierTests {

    private final JavaMailSender sender = mock(JavaMailSender.class);
    private final AuditLogService audit = mock(AuditLogService.class);
    private final EmailNotifier notifier = new EmailNotifier(sender, audit);

    private EmailNotifier notifier() {
        ReflectionTestUtils.setField(notifier, "origin", "noreply@echoes.test");
        return notifier;
    }

    private static MfaCodeEmailMessage message() {
        MfaCodeEmailMessage m = new MfaCodeEmailMessage();
        m.setData(new MfaCodeData("123456"));
        return m;
    }

    private AuditLog recorded() {
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(audit).recordEmailOutcome(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("Envio aceito pelo SMTP é auditado como SUCCESS")
    void sentIsAuditedAsSuccess() throws Exception {
        when(sender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));

        notifier().send("ana@exemplo.com", message());

        AuditLog log = recorded();
        assertEquals("SUCCESS", log.getStatus());
        assertEquals("SEND", log.getAction());
        assertEquals("Email", log.getEntity());
        assertTrue(log.getDetails().startsWith(EmailNotifier.RESULT_SENT));
        assertTrue(log.getDetails().contains("a***@exemplo.com"));
        assertFalse(log.getDetails().contains("ana@exemplo.com"));
    }

    @Test
    @DisplayName("Destinatário recusado pelo SMTP é auditado como inexistente")
    void recipientRejectedIsAuditedAsNotFound() throws Exception {
        when(sender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        SendFailedException rejected = new SendFailedException(
                "550 user unknown", null, null, null,
                new InternetAddress[] { new InternetAddress("ana@exemplo.com") });
        doThrow(new MailSendException(Map.of("ana", rejected))).when(sender).send(any(MimeMessage.class));

        notifier().send("ana@exemplo.com", message());

        AuditLog log = recorded();
        assertEquals("FAILED", log.getStatus());
        assertTrue(log.getDetails().startsWith(EmailNotifier.RESULT_NOT_FOUND));
    }

    @Test
    @DisplayName("Endereço com formato inválido é auditado como inexistente")
    void invalidAddressIsAuditedAsNotFound() throws Exception {
        when(sender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));

        notifier().send("nao-e-email", message());

        AuditLog log = recorded();
        assertEquals("FAILED", log.getStatus());
        assertTrue(log.getDetails().startsWith(EmailNotifier.RESULT_NOT_FOUND));
    }

    @Test
    @DisplayName("Erro de conexão é auditado como falha de envio, não como endereço inexistente")
    void connectionErrorIsAuditedAsFailure() throws Exception {
        when(sender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        doThrow(new MailSendException("connect timed out")).when(sender).send(any(MimeMessage.class));

        notifier().send("ana@exemplo.com", message());

        AuditLog log = recorded();
        assertEquals("FAILED", log.getStatus());
        assertTrue(log.getDetails().startsWith(EmailNotifier.RESULT_FAILED));
    }

    @Test
    @DisplayName("Máscara do e-mail não expõe o endereço completo")
    void maskHidesLocalPart() {
        assertEquals("j***@dominio.com.br", EmailNotifier.mask("joao@dominio.com.br"));
        assertEquals("***", EmailNotifier.mask("sem-arroba"));
    }
}
