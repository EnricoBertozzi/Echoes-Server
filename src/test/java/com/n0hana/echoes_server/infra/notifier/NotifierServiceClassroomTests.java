package com.n0hana.echoes_server.infra.notifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.n0hana.echoes_server.infra.notifier.data.ClassroomTeacherAddedData;
import com.n0hana.echoes_server.infra.notifier.message.ClassroomTeacherAddedEmailMessage;
import com.n0hana.echoes_server.infra.notifier.message.EmailMessage;

@ExtendWith(MockitoExtension.class)
class NotifierServiceClassroomTests {

    @Mock
    EmailNotifier emailNotifier;

    @InjectMocks
    NotifierService notifierService;

    @Test
    @DisplayName("Verifica o envio da notificação de professor adicionado à turma")
    void shouldVerifySendingOfClassroomTeacherAddedNotification() {
        String email = "professor@email.com";

        notifierService.sendClassroomTeacherAdded(email, "Anatomia", "2026/01");

        ArgumentCaptor<EmailMessage<?>> captor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailNotifier).send(eq(email), captor.capture());
        EmailMessage<?> message = captor.getValue();

        assertInstanceOf(ClassroomTeacherAddedEmailMessage.class, message);

        ClassroomTeacherAddedData data = (ClassroomTeacherAddedData) message.getDate();
        assertEquals("Anatomia", data.getClassroomTitle());
        assertEquals("2026/01", data.getPeriod());

        assertTrue(message.getTitle().contains("Anatomia"));
        assertTrue(message.getContent().contains("Anatomia"));
        assertTrue(message.getContent().contains("2026/01"));
    }
}
