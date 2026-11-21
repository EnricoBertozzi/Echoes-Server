
package com.n0hana.echoes_server.infra.logs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Optional;
import java.util.UUID;

import org.aspectj.lang.JoinPoint;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.mockito.ArgumentCaptor;

import org.springframework.context.ApplicationEventPublisher;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.n0hana.echoes_server.institution.InstitutionModel;
import com.n0hana.echoes_server.institution.InstitutionRepository;
import com.n0hana.echoes_server.user.model.Manager;

import jakarta.servlet.http.HttpServletRequest;

class AuditAspectTests {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void gestorTemInstituicaoNoEventoInstitucionalSemResultado() {
        UUID institutionId = UUID.randomUUID();

        InstitutionModel institution =
                new InstitutionModel();

        institution.setId(institutionId);

        Manager manager = new Manager(institution);
        manager.setId(UUID.randomUUID());

        SecurityContextHolder
                .getContext()
                .setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                        manager,
                        null,
                        manager.getAuthorities()
                    )
                );

        ApplicationEventPublisher publisher =
                mock(ApplicationEventPublisher.class);

        HttpServletRequest request =
                mock(HttpServletRequest.class);

        AuditLogService auditLogService =
                mock(AuditLogService.class);

        AuditAspect aspect =
                new AuditAspect(
                    publisher,
                    request,
                    auditLogService,
                    mock(InstitutionRepository.class)
                );

        Auditable auditable =
                annotation("DELETE", "Student");

        JoinPoint joinPoint =
                mock(JoinPoint.class);

        aspect.onFailure(
            joinPoint,
            auditable,
            new IllegalStateException("Falha")
        );

        ArgumentCaptor<AuditLog> captor =
                ArgumentCaptor.forClass(
                    AuditLog.class
                );

        // falhas vão pelo caminho síncrono de auditoria (B4)
        verify(auditLogService).recordFailure(
            captor.capture()
        );

        assertEquals(
            institutionId.toString(),
            captor.getValue().getInstitutionId()
        );

        assertEquals(
            "FAILED",
            captor.getValue().getStatus()
        );
    }

    @Test
    void gestorNaoConverteEventoGlobalEmInstitucional() {
        UUID institutionId = UUID.randomUUID();

        InstitutionModel institution =
                new InstitutionModel();

        institution.setId(institutionId);

        Manager manager =
                new Manager(institution);

        manager.setId(UUID.randomUUID());

        SecurityContextHolder
                .getContext()
                .setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                        manager,
                        null,
                        manager.getAuthorities()
                    )
                );

        ApplicationEventPublisher publisher =
                mock(ApplicationEventPublisher.class);

        AuditLogService auditLogService = mock(AuditLogService.class);

        AuditAspect aspect =
                new AuditAspect(
                    publisher,
                    mock(HttpServletRequest.class),
                    auditLogService,
                    mock(InstitutionRepository.class)
                );

        aspect.onFailure(
            mock(JoinPoint.class),
            annotation("CREATE", "Animal"),
            new IllegalArgumentException()
        );

        ArgumentCaptor<AuditLog> captor =
                ArgumentCaptor.forClass(
                    AuditLog.class
                );

        // falhas vão pelo caminho síncrono de auditoria (B4)
        verify(auditLogService).recordFailure(
            captor.capture()
        );

        assertNull(
            captor.getValue().getInstitutionId()
        );
    }

    private static Auditable annotation(
            String action,
            String entity
    ) {
        return new Auditable() {

            @Override
            public String action() {
                return action;
            }

            @Override
            public String entity() {
                return entity;
            }

            @Override
            public String entityId() {
                return "";
            }

            @Override
            public String entityName() {
                return "";
            }

            @Override
            public String details() {
                return "";
            }

            @Override
            public String institutionId() {
                return "";
            }

            @Override
            public Class<Auditable> annotationType() {
                return Auditable.class;
            }
        };
    }

    @Test
    @DisplayName("Login com senha errada grava FAILED de forma síncrona (B4)")
    void failedLoginIsRecordedSynchronously() throws Exception {
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        AuditAspect aspect = new AuditAspect(publisher, mock(HttpServletRequest.class), auditLogService,
                mock(InstitutionRepository.class));

        Auditable auditable = Sample.class.getDeclaredMethod("login").getAnnotation(Auditable.class);

        aspect.onFailure(mock(JoinPoint.class), auditable,
                new com.n0hana.echoes_server.auth.exception.AuthFailedException());

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogService).recordFailure(captor.capture());
        assertEquals("FAILED", captor.getValue().getStatus());
        assertEquals("LOGIN_ATTEMPT", captor.getValue().getAction());
        assertEquals("User", captor.getValue().getEntity());
        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("Gestor com instituição LAZY: ator mantém nome, papel e instituição (regressão)")
    void managerActorKeepsNameWhenInstitutionIsLazy() throws Exception {
        UUID institutionId = UUID.randomUUID();
        InstitutionModel institution = new InstitutionModel();
        institution.setId(institutionId);

        Manager manager = new Manager(institution);
        manager.setId(UUID.randomUUID());
        manager.setName("Ana Gestora");

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(manager, null, manager.getAuthorities()));

        InstitutionRepository institutions = mock(InstitutionRepository.class);
        InstitutionModel stored = new InstitutionModel();
        stored.setId(institutionId);
        stored.setName("Escola Teste");
        when(institutions.findById(institutionId)).thenReturn(Optional.of(stored));

        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        AuditAspect aspect = new AuditAspect(publisher, mock(HttpServletRequest.class),
                auditLogService, institutions);

        aspect.onFailure(mock(JoinPoint.class),
                annotation("LOGOUT", "User"),
                new IllegalStateException("x"));

        ArgumentCaptor<AuditLog> recorded = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogService).recordFailure(recorded.capture());
        assertEquals("Ana Gestora", recorded.getValue().getActorName());
        assertEquals("MANAGER", recorded.getValue().getActorRole());
        assertEquals("Escola Teste", recorded.getValue().getActorInstitutionName());
    }

    @Test
    @DisplayName("L11: e-mail e token na mensagem da exceção não chegam ao details da auditoria")
    void failureDetailsDoNotLeakPii() throws Exception {
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        AuditAspect aspect = new AuditAspect(publisher, mock(HttpServletRequest.class), auditLogService,
                mock(InstitutionRepository.class));
        Auditable auditable = Sample.class.getDeclaredMethod("login").getAnnotation(Auditable.class);

        aspect.onFailure(mock(JoinPoint.class), auditable, new IllegalStateException(
                "Usuário maria.silva@escola.edu.br falhou com Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.assinaturaabc"));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogService).recordFailure(captor.capture());
        String details = captor.getValue().getDetails();

        assertTrue(details.startsWith("IllegalStateException"), details);
        assertFalse(details.contains("maria.silva"), details);
        assertFalse(details.contains("@"), details);
        assertFalse(details.contains("eyJ"), details);
        assertTrue(details.length() <= 255);
    }

    static class Sample {
        @Auditable(action = "LOGIN_ATTEMPT", entity = "User")
        public void login() {
        }
    }
}
