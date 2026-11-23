package com.n0hana.echoes_server.infra.logs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;

import com.n0hana.echoes_server.user.model.User;
import com.n0hana.echoes_server.user.model.UserRole;

class AuditLogControllerTests {

    private final AuditLogService service = mock(AuditLogService.class);
    private final AuditInstitutionNames institutionNames = mock(AuditInstitutionNames.class);
    private final AuditLogController controller = new AuditLogController(service, institutionNames);

    private AuditLog log() {
        AuditLog log = AuditLog.builder().action("CREATE").entity("Classroom").entityName("Turma 1")
                .status("SUCCESS").ip("203.0.113.7").build();
        return log;
    }

    private ResponseEntity<?> list(UserRole role) {
        User user = mock(User.class);
        when(user.getUserRole()).thenReturn(role);
        when(service.getLogs(any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(log())));
        when(institutionNames.resolve(any())).thenReturn(log -> "Instituição de exemplo");
        return controller.list(user, null, null, null, null, PageRequest.of(0, 20));
    }

    @Test
    @DisplayName("ADMIN recebe o IP e o nome da entidade")
    @SuppressWarnings("unchecked")
    void adminSeesIp() {
        var body = (org.springframework.data.domain.Page<AuditLogDTO>) list(UserRole.ADMIN).getBody();
        assertEquals("203.0.113.7", body.getContent().get(0).ip());
        assertEquals("Turma 1", body.getContent().get(0).entityName());
        assertEquals("Instituição de exemplo", body.getContent().get(0).institutionName());
    }

    @Test
    @DisplayName("MANAGER recebe ip nulo")
    @SuppressWarnings("unchecked")
    void managerDoesNotSeeIp() {
        var body = (org.springframework.data.domain.Page<AuditLogDTO>) list(UserRole.MANAGER).getBody();
        assertNull(body.getContent().get(0).ip());
        assertEquals("Turma 1", body.getContent().get(0).entityName());
    }

    @Test
    @DisplayName("Integridade devolve a contagem e os ids adulterados (S15)")
    @SuppressWarnings("unchecked")
    void integrityReportsTampered() {
        UUID id = UUID.randomUUID();
        when(service.findTamperedIds()).thenReturn(List.of(id));
        Map<String, Object> body = (Map<String, Object>) controller.integrity().getBody();
        assertEquals(1, body.get("tamperedCount"));
        assertEquals(List.of(id), body.get("tamperedIds"));

        when(service.findTamperedIds()).thenReturn(List.of());
        body = (Map<String, Object>) controller.integrity().getBody();
        assertEquals(0, body.get("tamperedCount"));
    }
}
