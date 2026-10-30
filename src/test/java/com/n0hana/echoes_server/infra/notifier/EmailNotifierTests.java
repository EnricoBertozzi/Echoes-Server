package com.n0hana.echoes_server.infra.notifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Properties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import com.n0hana.echoes_server.infra.notifier.message.EmailMessage;

import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

@ExtendWith(MockitoExtension.class)
class EmailNotifierTests {

    @Mock
    private JavaMailSender javaMailSender;

    @InjectMocks
    private EmailNotifier emailNotifier;

    @BeforeEach
    void setUp() {
        String origin = "echoes@email.com";

        // Injeta dentro do campo origin o valor do remetente, devido ao valor ser
        // passado por @Value
        ReflectionTestUtils.setField(emailNotifier, "origin", origin);
    }

    @Test
    @DisplayName("Deve enviar um e-mail com os dados configurados")
    void shouldSendEmailWithCorrectConfiguration() throws Exception {
        // Arrange
        String origin = "echoes@email.com";
        String recipient = "example@email.com";
        String subject = "Código de autenticação";
        String content = "<h1>123456</h1>";

        MimeMessage mimeMessage = new MimeMessage(
                Session.getInstance(new Properties()));

        EmailMessage<?> message = mock(EmailMessage.class);

        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(message.getTitle()).thenReturn(subject);
        when(message.getContent()).thenReturn(content);

        // Act
        emailNotifier.send(recipient, message);
        mimeMessage.saveChanges();

        // Assert
        verify(javaMailSender).send(mimeMessage);

        assertEquals(origin, ((InternetAddress) mimeMessage.getFrom()[0]).getAddress());

        assertEquals(recipient, (
                (InternetAddress) mimeMessage.getRecipients(Message.RecipientType.TO)[0]
            ).getAddress());

        assertEquals(subject, mimeMessage.getSubject());

        assertTrue(mimeMessage.isMimeType("text/html"));
        assertEquals(content, mimeMessage.getContent());
    }

    @Test
    @DisplayName("Não deve enviar e-mails quando ocorrer falha na criação da mensagem")
    void shouldNotSendEmailWhenMimeMessageCreationFails() {
        // Arrange
        String recipient = "example@email.com";

        EmailMessage<?> message = mock(EmailMessage.class);

        when(javaMailSender.createMimeMessage())
                .thenThrow(new MailSendException("Falha ao criar a mensagem"));

        // Act e Assert
        assertDoesNotThrow(() -> emailNotifier.send(recipient, message));

        verify(javaMailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("Deve tratar falha ao enviar e-mail")
    void shouldHandleEmailSendingFailure() throws Exception {
        // Arrange
        String recipient = "example@email.com";
        String subject = "Código de autenticação";
        String content = "123456";

        MimeMessage mimeMessage = new MimeMessage(
                Session.getInstance(new Properties())
        );

        EmailMessage<?> message = mock(EmailMessage.class);

        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(message.getTitle()).thenReturn(subject);
        when(message.getContent()).thenReturn(content);

        doThrow(new MailSendException("Falha no servidor SMTP")).when(javaMailSender).send(mimeMessage);

        // Act e Assert
        assertDoesNotThrow(() -> emailNotifier.send(recipient, message));

        verify(javaMailSender).send(mimeMessage);
    }
}