
package com.n0hana.echoes_server.infra.logs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.aspectj.lang.JoinPoint;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.mockito.ArgumentCaptor;

import org.springframework.context.ApplicationEventPublisher;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.n0hana.echoes_server.institution.InstitutionModel;
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

        AuditAspect aspect =
                new AuditAspect(
                    publisher,
                    request
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

        verify(publisher).publishEvent(
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

        AuditAspect aspect =
                new AuditAspect(
                    publisher,
                    mock(HttpServletRequest.class)
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

        verify(publisher).publishEvent(
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
            public String institutionId() {
                return "";
            }

            @Override
            public Class<Auditable> annotationType() {
                return Auditable.class;
            }
        };
    }
}

