
package com.n0hana.echoes_server.infra.logs;

import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;

import com.n0hana.echoes_server.device.DeviceScope;
import com.n0hana.echoes_server.device.port.DeviceScopeResolver;
import com.n0hana.echoes_server.institution.InstitutionModel;
import com.n0hana.echoes_server.user.model.Admin;
import com.n0hana.echoes_server.user.model.Manager;
import com.n0hana.echoes_server.user.model.Student;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTests {

    @Mock
    private AuditLogRepository repository;

    @Mock
    private DeviceScopeResolver scopeResolver;

    @InjectMocks
    private AuditLogService service;

    private final Pageable pageable =
            PageRequest.of(0, 20);

    @Test
    void adminPodeConsultarGlobalmente() {
        Admin admin = new Admin();

        when(scopeResolver.resolve(admin))
                .thenReturn(new DeviceScope.All());

        when(
            repository.findAll(
                any(Specification.class),
                eq(pageable)
            )
        ).thenReturn(Page.empty(pageable));

        service.getLogs(
            admin,
            null,
            null,
            null,
            null,
            pageable
        );

        verify(repository).findAll(
            any(Specification.class),
            eq(pageable)
        );
    }

    @Test
    void gestorConsultaPropriaInstituicao() {
        UUID institutionId = UUID.randomUUID();

        Manager manager = manager(institutionId);

        when(scopeResolver.resolve(manager))
                .thenReturn(
                    new DeviceScope.InstitutionScope(
                        institutionId
                    )
                );

        when(
            repository.findAll(
                any(Specification.class),
                eq(pageable)
            )
        ).thenReturn(Page.empty(pageable));

        service.getLogs(
            manager,
            institutionId,
            "Classroom",
            "CREATE",
            "SUCCESS",
            pageable
        );

        verify(repository).findAll(
            any(Specification.class),
            eq(pageable)
        );
    }

    @Test
    void gestorNaoPodeFiltrarOutraInstituicao() {
        UUID ownId = UUID.randomUUID();

        Manager manager = manager(ownId);

        when(scopeResolver.resolve(manager))
                .thenReturn(
                    new DeviceScope.InstitutionScope(
                        ownId
                    )
                );

        assertThrows(
            AccessDeniedException.class,
            () -> service.getLogs(
                manager,
                UUID.randomUUID(),
                null,
                null,
                null,
                pageable
            )
        );

        verifyNoInteractions(repository);
    }

    @Test
    void estudanteNaoPodeConsultar() {
        assertThrows(
            AccessDeniedException.class,
            () -> service.getLogs(
                new Student(),
                null,
                null,
                null,
                null,
                pageable
            )
        );

        verifyNoInteractions(
            scopeResolver,
            repository
        );
    }

    @Test
    void statusInvalidoRetorna400() {
        Admin admin = new Admin();

        when(scopeResolver.resolve(admin))
                .thenReturn(new DeviceScope.All());

        assertThrows(
            ResponseStatusException.class,
            () -> service.getLogs(
                admin,
                null,
                null,
                null,
                "UNKNOWN",
                pageable
            )
        );

        verifyNoInteractions(repository);
    }

    @Test
    @SuppressWarnings("unchecked")
    void filtroInstitucionalEhImpostoNaConsultaJpa() {
        UUID institutionId = UUID.randomUUID();

        Manager manager = manager(institutionId);

        when(scopeResolver.resolve(manager))
                .thenReturn(
                    new DeviceScope.InstitutionScope(
                        institutionId
                    )
                );

        when(
            repository.findAll(
                any(Specification.class),
                eq(pageable)
            )
        ).thenReturn(Page.empty(pageable));

        service.getLogs(
            manager,
            null,
            null,
            null,
            null,
            pageable
        );

        ArgumentCaptor<Specification<AuditLog>> captor =
                ArgumentCaptor.forClass(
                    Specification.class
                );

        verify(repository).findAll(
            captor.capture(),
            eq(pageable)
        );

        Root<AuditLog> root = mock(Root.class);
        Path<String> path = mock(Path.class);
        CriteriaBuilder builder = mock(CriteriaBuilder.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);

        when(root.<String>get("institutionId"))
                .thenReturn(path);

        captor.getValue().toPredicate(
            root,
            query,
            builder
        );

        verify(builder).equal(
            path,
            institutionId.toString()
        );
    }

    private Manager manager(
            UUID institutionId
    ) {
        InstitutionModel institution =
                new InstitutionModel();

        institution.setId(institutionId);

        return new Manager(institution);
    }
}

