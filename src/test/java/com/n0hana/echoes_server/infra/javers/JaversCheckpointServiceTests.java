package com.n0hana.echoes_server.infra.javers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.n0hana.echoes_server.infra.javers.checkpoint.JaversCheckpoint;
import com.n0hana.echoes_server.infra.javers.checkpoint.JaversCheckpointRepository;
import com.n0hana.echoes_server.infra.javers.checkpoint.JaversCheckpointService;
import com.n0hana.echoes_server.infra.javers.checkpoint.JaversCheckpointStatus;
import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.model.Student;

@SpringBootTest
@ActiveProfiles("test")
class JaversCheckpointServiceTests {

    @Autowired private JaversCheckpointService service;
    @Autowired private JaversCheckpointRepository repository;
    @Autowired private UserRepository users;

    private Student student;

    @AfterEach
    void cleanup() {
        repository.deleteAll();
        if (student != null)
            users.findById(student.getId()).ifPresent(users::delete);
    }

    private JaversCheckpoint newCheckpoint(String requestedBy) {
        student = JaversTestSupport.studentWithSnapshots(users, 1);
        return service.create(Student.class.getName(), List.<Object>of(student), requestedBy);
    }

    @Test
    @DisplayName("create registra o commit, os ids e deixa o checkpoint aguardando aprovação")
    void createsPendingCheckpoint() {
        JaversCheckpoint cp = newCheckpoint("admin-1");

        assertNotNull(cp.getCommitId());
        assertEquals(1, cp.getAffectedCount());
        assertEquals(student.getId().toString(), cp.getEntityIds());
        assertEquals(JaversCheckpointStatus.PENDING_APPROVAL, cp.getStatus());
    }

    @Test
    @DisplayName("Aprovar com o mesmo usuário que solicitou falha com IllegalStateException")
    void sameUserCannotApprove() {
        JaversCheckpoint cp = newCheckpoint("admin-1");

        assertThrows(IllegalStateException.class, () -> service.approve(cp.getId(), "admin-1"));
        assertThrows(IllegalStateException.class, () -> service.requireApproved(cp.getId()));
    }

    @Test
    @DisplayName("Outro usuário aprova; depois o checkpoint pode ser marcado como executado")
    void differentUserApprovesAndExecutes() {
        JaversCheckpoint cp = newCheckpoint("admin-1");

        service.approve(cp.getId(), "admin-2");
        assertEquals("admin-2", service.requireApproved(cp.getId()).getApprovedBy());
        assertThrows(IllegalStateException.class, () -> service.approve(cp.getId(), "admin-3"), "já aprovado");

        service.markExecuted(cp.getId());
        JaversCheckpoint done = repository.findById(cp.getId()).orElseThrow();
        assertEquals(JaversCheckpointStatus.EXECUTED, done.getStatus());
        assertNotNull(done.getExecutedAt());
        assertThrows(IllegalStateException.class, () -> service.requireApproved(cp.getId()));
    }

    @Test
    @DisplayName("Checkpoint inexistente ou sem entidades é rejeitado")
    void invalidCheckpoints() {
        assertThrows(java.util.NoSuchElementException.class, () -> service.approve(UUID.randomUUID(), "x"));
        assertThrows(IllegalArgumentException.class, () -> service.create("T", List.of(), "x"));
    }
}
