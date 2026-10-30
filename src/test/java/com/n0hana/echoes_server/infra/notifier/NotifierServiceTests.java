package com.n0hana.echoes_server.infra.notifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.n0hana.echoes_server.infra.notifier.data.InstituitionVerificationCompletedData;
import com.n0hana.echoes_server.infra.notifier.data.InstitutionPendingVerificationData;
import com.n0hana.echoes_server.infra.notifier.data.InstitutionVerificationRejectedData;
import com.n0hana.echoes_server.infra.notifier.data.MfaCodeData;
import com.n0hana.echoes_server.infra.notifier.data.PasswordResetData;
import com.n0hana.echoes_server.infra.notifier.data.RegisterInviteData;
import com.n0hana.echoes_server.infra.notifier.message.EmailMessage;
import com.n0hana.echoes_server.infra.notifier.message.InstitutionPendingVerificationEmailMessage;
import com.n0hana.echoes_server.infra.notifier.message.InstitutionVerificationCompletedEmailMessage;
import com.n0hana.echoes_server.infra.notifier.message.InstitutionVerificationRejectedEmailMessage;
import com.n0hana.echoes_server.infra.notifier.message.MfaCodeEmailMessage;
import com.n0hana.echoes_server.infra.notifier.message.PasswordResetEmailMessage;
import com.n0hana.echoes_server.infra.notifier.message.RegisterInviteMessage;

@ExtendWith(MockitoExtension.class)
public class NotifierServiceTests {
    
    @Mock 
    EmailNotifier emailNotifier;

    @InjectMocks
    NotifierService notifierService;

    @Test 
    @DisplayName("Verifica o envio da notificação de dois fatores")
    void shouldVerifySendingOfMfaFactorNotification() {
        // Arrange
        String email = "example@email.com";
        String code = "123456";

        // Act
        notifierService.sendMfaCode(email, code);

        // Assert
        EmailMessage<?> message = captureMessage(email);

        assertInstanceOf(MfaCodeEmailMessage.class, message);

        MfaCodeData data = (MfaCodeData) message.getDate();
        assertEquals(code, data.getCode());
        assertTrue(message.getContent().contains(code));
    }

    @Test 
    @DisplayName("Verifica o envio da notificação de redefinição de senha")
    void shouldVerifySendingOfPasswordResetNotification() {
        // Arrange
        String email = "example@email.com";
        String code = "123456";

        // Act
        notifierService.sendPasswordReset(email, code);

        // Assert
        EmailMessage<?> message = this.captureMessage(email);

        assertInstanceOf(PasswordResetEmailMessage.class, message);
        PasswordResetData data = (PasswordResetData) message.getDate();

        assertEquals(code, data.getCode());
        assertTrue(message.getContent().contains(code));
    }

    @Test 
    @DisplayName("Verifica o envio da notificação de convite de novo usuário")
    void shouldVerifySendingOfRegisterInviteNotification() {
        // Arrange
        String email = "example@email.com";
        String code = "123456";
        
        //Act
        notifierService.sendRegisterInvite(email, code, email);

        // Assert
        EmailMessage<?> message = this.captureMessage(email);
        assertInstanceOf(RegisterInviteMessage.class, message);

        RegisterInviteData data = (RegisterInviteData) message.getDate();
        assertEquals(code, data.getCode());
        assertEquals(email, data.getEmail());

        assertTrue(message.getContent().contains(code));
        assertTrue(message.getContent().contains(email));
    }

    @Test 
    @DisplayName("Verifica o envio da notificação de verficação de instituição pendente")
    void shouldVerifySendingOfInstitutionPendingNotification() {
        // Arrange
        String email = "example@email.com";
        String name = "instituicao";
        String cnpj = "00.000.000/0001-00";
        String reason = "Verificação em andamento";
        
        // Act
        notifierService.sendInstitutionPending(email, name, cnpj, reason);

        // Assert
        EmailMessage<?> message = this.captureMessage(email);
        assertInstanceOf(InstitutionPendingVerificationEmailMessage.class, message);

        InstitutionPendingVerificationData data = (InstitutionPendingVerificationData) message.getDate();
        assertEquals(name, data.getName());
        assertEquals(cnpj, data.getCnpj());
        assertEquals(reason, data.getReason());
        
        assertTrue(message.getTitle().contains(name));

        assertTrue(message.getContent().contains(name));
        assertTrue(message.getContent().contains(cnpj));
        assertTrue(message.getContent().contains(reason));
    }

    @Test 
    @DisplayName("Verifica o envio da notificação de erro na verificação de instituição")
    void shouldVerifySendingOfInstitutionRejectedNotification() {
        // Arrange
        String email = "example@email.com";
        String name = "instituicao";
        String cnpj = "00.000.000/0001-00";
        String reason = "Falha ao se comunicar com o Brasil";
        
        // Act
        notifierService.sendInstitutionRejected(email, name, cnpj, reason);

        // Assert
        EmailMessage<?> message = this.captureMessage(email);
        assertInstanceOf(InstitutionVerificationRejectedEmailMessage.class, message);

        InstitutionVerificationRejectedData data = (InstitutionVerificationRejectedData) message.getDate();
        assertEquals(name, data.getName());
        assertEquals(cnpj, data.getCnpj());
        assertEquals(reason, data.getReason());
        
        assertTrue(message.getTitle().contains(name));

        assertTrue(message.getContent().contains(name));
        assertTrue(message.getContent().contains(cnpj));
        assertTrue(message.getContent().contains(reason));
    }

    @Test 
    @DisplayName("Verifica o envio da notificação de sucesso na verificação de instituição")
    void shouldVerifySendingOfInstitutionCompletedNotification() {
        String email = "example@email.com";
        String name = "instituicao";
        String cnpj = "00.000.000/0001-00";
        
        // Act
        notifierService.sendInstituitionCompleted(email, name, cnpj);

        // Assert
        EmailMessage<?> message = this.captureMessage(email);
        assertInstanceOf(InstitutionVerificationCompletedEmailMessage.class, message);

        InstituitionVerificationCompletedData data = (InstituitionVerificationCompletedData) message.getDate();
        assertEquals(name, data.getName());
        assertEquals(cnpj, data.getCnpj());
        
        assertTrue(message.getTitle().contains(name));

        assertTrue(message.getContent().contains(name));
        assertTrue(message.getContent().contains(cnpj));
    }

    private EmailMessage<?> captureMessage(String recipient) {
    ArgumentCaptor<EmailMessage<?>> captor =
        ArgumentCaptor.forClass(EmailMessage.class);

    verify(emailNotifier).send(
        eq(recipient),
        captor.capture()
    );

    return captor.getValue();
}
}
