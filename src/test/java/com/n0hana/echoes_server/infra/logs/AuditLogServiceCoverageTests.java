package com.n0hana.echoes_server.infra.logs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import com.n0hana.echoes_server.device.DeviceScope;
import com.n0hana.echoes_server.device.port.DeviceScopeResolver;
import com.n0hana.echoes_server.user.model.Admin;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

@DisplayName("AuditLogService: gravação e consulta")
class AuditLogServiceCoverageTests {

    private AuditLogRepository repository;
    private DeviceScopeResolver scopeResolver;
    private AuditSigner signer;
    private AuditLogService service;
    private final Pageable pageable = PageRequest.of(0, 10);

    @BeforeEach
    void setUp() {
        repository = mock(AuditLogRepository.class);
        scopeResolver = mock(DeviceScopeResolver.class);
        signer = mock(AuditSigner.class);
        when(signer.sign(any())).thenReturn("assinatura");
        service = new AuditLogService(repository, scopeResolver, signer);
    }

    private AuditLog event(String status) {
        return AuditLog.builder().userId("u1").action("CREATE").entity("Classroom").status(status)
                .details("detalhe").ip("192.0.2.1").entityId("e1").entityName("n").institutionId("i1")
                .actorName("Ana").actorRole("MANAGER").actorInstitutionName("Escola").build();
    }

    private AuditLog saved() {
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("onCommit grava cópia nova, com timestamp em segundos e assinatura, só para SUCCESS")
    void onCommitSuccess() {
        AuditLog original = event("SUCCESS");

        service.onCommit(original);

        AuditLog fresh = saved();
        assertTrue(fresh != original);
        assertEquals("CREATE", fresh.getAction());
        assertEquals("Escola", fresh.getActorInstitutionName());
        assertEquals("assinatura", fresh.getSignature());
        assertEquals(0, fresh.getTimestamp().getNano());
    }

    @Test
    @DisplayName("onCommit ignora eventos que não são SUCCESS")
    void onCommitIgnoraOutros() {
        service.onCommit(event("FAILED"));

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("onRollback converte SUCCESS em FAILED com explicação")
    void onRollbackSuccess() {
        service.onRollback(event("SUCCESS"));

        AuditLog fresh = saved();
        assertEquals("FAILED", fresh.getStatus());
        assertTrue(fresh.getDetails().contains("rollback"));
    }

    @Test
    @DisplayName("onRollback preserva eventos que já eram FAILED")
    void onRollbackFailed() {
        service.onRollback(event("FAILED"));

        AuditLog fresh = saved();
        assertEquals("FAILED", fresh.getStatus());
        assertEquals("detalhe", fresh.getDetails());
    }

    @Test
    @DisplayName("recordFailure e recordEmailOutcome gravam o evento")
    void recordMethods() {
        service.recordFailure(event("FAILED"));
        service.recordEmailOutcome(event("SUCCESS"));

        verify(repository, org.mockito.Mockito.times(2)).save(any(AuditLog.class));
    }

    @Test
    @DisplayName("falha ao gravar é registrada e não propaga")
    void falhaAoGravar() {
        when(repository.save(any())).thenThrow(new RuntimeException("banco fora"));

        service.recordFailure(event("FAILED"));

        verify(repository).save(any());
    }

    @Test
    @DisplayName("findTamperedIds devolve só os ids cuja assinatura não confere")
    void tampered() {
        AuditLog ok = event("SUCCESS");
        ok.setId(UUID.randomUUID());
        AuditLog bad = event("SUCCESS");
        bad.setId(UUID.randomUUID());
        when(repository.findAll()).thenReturn(List.of(ok, bad));
        when(signer.verify(ok)).thenReturn(true);
        when(signer.verify(bad)).thenReturn(false);

        assertEquals(List.of(bad.getId()), service.findTamperedIds());
    }

    @Test
    @DisplayName("getLogs nega usuário nulo")
    void usuarioNulo() {
        assertThrows(AccessDeniedException.class, () -> service.getLogs(null, null, null, null, null, pageable));
    }

    @Test
    @DisplayName("getLogs recusa status fora de SUCCESS/FAILED, mas aceita FAILED")
    void status() {
        Admin admin = new Admin();
        when(scopeResolver.resolve(admin)).thenReturn(new DeviceScope.All());
        Page<AuditLog> page = new PageImpl<>(List.of());
        when(repository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.getLogs(admin, null, null, null, "PENDING", pageable));
        assertEquals(400, e.getStatusCode().value());

        assertSame(page, service.getLogs(admin, null, null, null, "FAILED", pageable));
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private int predicatesFor(String institutionId, String entity, String action, String status) {
        Admin admin = new Admin();
        UUID inst = institutionId == null ? null : UUID.fromString(institutionId);
        when(scopeResolver.resolve(admin)).thenReturn(new DeviceScope.All());
        when(repository.findAll(any(Specification.class), eq(pageable))).thenReturn(Page.empty());

        service.getLogs(admin, inst, entity, action, status, pageable);

        ArgumentCaptor<Specification<AuditLog>> captor = ArgumentCaptor.forClass(Specification.class);
        verify(repository).findAll(captor.capture(), eq(pageable));

        Root<AuditLog> root = mock(Root.class);
        CriteriaBuilder builder = mock(CriteriaBuilder.class);
        Path path = mock(Path.class);
        when(root.get(anyString())).thenReturn(path);
        when(builder.equal(any(), any(Object.class))).thenReturn(mock(Predicate.class));
        ArgumentCaptor<Predicate[]> preds = ArgumentCaptor.forClass(Predicate[].class);
        when(builder.and(preds.capture())).thenReturn(mock(Predicate.class));

        captor.getValue().toPredicate(root, mock(CriteriaQuery.class), builder);
        return preds.getValue().length;
    }

    @Test
    @DisplayName("specification sem filtros não adiciona predicados")
    void semFiltros() {
        assertEquals(0, predicatesFor(null, null, null, null));
    }

    @Test
    @DisplayName("specification adiciona um predicado por filtro informado; em branco é ignorado")
    void comFiltros() {
        assertEquals(0, predicatesFor(null, "  ", "", null));
    }

    @Test
    @DisplayName("specification com todos os filtros tem 4 predicados")
    void todosFiltros() {
        assertEquals(4, predicatesFor(UUID.randomUUID().toString(), " Classroom ", "CREATE", "SUCCESS"));
    }
}
