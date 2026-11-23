package com.n0hana.echoes_server.infra.logs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("AuditLog, AuditLogDTO e AuditRetentionScheduler")
class AuditLogModelCoverageTests {

    private AuditLog full() {
        AuditLog l = AuditLog.builder().userId("u1").action("UPDATE").entity("Device").status("SUCCESS")
                .details("det").ip("192.0.2.9").entityId("e1").entityName("Sala").institutionId("i1")
                .actorName("Ana").actorRole("MANAGER").actorInstitutionName("Escola").build();
        l.setId(UUID.randomUUID());
        l.setTimestamp(Instant.parse("2025-03-01T00:00:00Z"));
        l.setSignature("sig");
        return l;
    }

    @Test
    @DisplayName("builder preenche todos os campos")
    void builder() {
        AuditLog l = full();

        assertEquals("u1", l.getUserId());
        assertEquals("UPDATE", l.getAction());
        assertEquals("Device", l.getEntity());
        assertEquals("SUCCESS", l.getStatus());
        assertEquals("det", l.getDetails());
        assertEquals("192.0.2.9", l.getIp());
        assertEquals("e1", l.getEntityId());
        assertEquals("Sala", l.getEntityName());
        assertEquals("i1", l.getInstitutionId());
        assertEquals("Ana", l.getActorName());
        assertEquals("MANAGER", l.getActorRole());
        assertEquals("Escola", l.getActorInstitutionName());
    }

    @Test
    @DisplayName("exclusão pelo callback @PreRemove é proibida")
    void preRemove() throws Exception {
        Method m = AuditLog.class.getDeclaredMethod("preventDelete");
        m.setAccessible(true);

        InvocationTargetException e = assertThrows(InvocationTargetException.class, () -> m.invoke(full()));
        assertInstanceOf(UnsupportedOperationException.class, e.getCause());
    }

    @Test
    @DisplayName("DTO expõe o IP por padrão")
    void dtoComIp() {
        AuditLog l = full();

        AuditLogDTO dto = AuditLogDTO.from(l);

        assertEquals(l.getId(), dto.id());
        assertEquals("192.0.2.9", dto.ip());
        assertEquals("Ana", dto.actorName());
        assertEquals("MANAGER", dto.actorRole());
        assertEquals("Escola", dto.actorInstitutionName());
        assertEquals("Sala", dto.entityName());
        assertEquals("i1", dto.institutionId());
        assertEquals(l.getTimestamp(), dto.timestamp());
    }

    @Test
    @DisplayName("DTO omite o IP quando solicitado")
    void dtoSemIp() {
        assertNull(AuditLogDTO.from(full(), false).ip());
    }

    @Test
    @DisplayName("scheduler não expurga quando desativado")
    void schedulerDesativado() {
        AuditRetentionService service = mock(AuditRetentionService.class);
        AuditRetentionScheduler scheduler = new AuditRetentionScheduler(service);
        ReflectionTestUtils.setField(scheduler, "enabled", false);

        scheduler.run();

        verify(service, never()).purge();
    }

    @Test
    @DisplayName("scheduler expurga quando ativado")
    void schedulerAtivado() {
        AuditRetentionService service = mock(AuditRetentionService.class);

        new AuditRetentionScheduler(service).run();

        verify(service).purge();
    }

    @Test
    @DisplayName("scheduler engole falhas do expurgo para tentar no dia seguinte")
    void schedulerEngoleFalha() {
        AuditRetentionService service = mock(AuditRetentionService.class);
        when(service.purge()).thenThrow(new IllegalStateException("gatilho recusou"));

        new AuditRetentionScheduler(service).run();

        verify(service).purge();
    }
}
