package com.n0hana.echoes_server.infra.javers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.javers.core.Javers;
import org.javers.core.json.JsonConverter;
import org.javers.core.metamodel.object.CdoSnapshot;
import org.javers.repository.jql.QueryBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.n0hana.echoes_server.infra.javers.baseline.UserJaversBaseline;
import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.model.Student;
import com.n0hana.echoes_server.user.model.User;

@SpringBootTest
@ActiveProfiles("test")
class JaversVersioningTests {

    private static final String BCRYPT = "$2a$10$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZ01234";

    @Autowired private UserRepository users;
    @Autowired private Javers javers;
    @Autowired private UserJaversBaseline baseline;

    private UUID id;

    @AfterEach
    void cleanup() {
        if (id != null)
            users.findById(id).ifPresent(users::delete);
    }

    private Student newStudent() {
        Student s = new Student();
        s.setEmail("jv-" + UUID.randomUUID() + "@test.local");
        s.setName("Titular Javers");
        s.setPassword(BCRYPT);
        return users.save(s);
    }

    @Test
    @DisplayName("Salvar usuário gera snapshot sem senha; contador de tentativas não gera snapshot novo")
    void snapshotOmitsPasswordAndIgnoresVolatileFields() {
        Student student = newStudent();
        id = student.getId();

        List<CdoSnapshot> snapshots = javers.findSnapshots(QueryBuilder.byInstanceId(id, Student.class).build());
        assertEquals(1, snapshots.size());
        JsonConverter json = javers.getJsonConverter();
        String state = json.toJson(snapshots.get(0).getState());
        assertTrue(state.contains(student.getEmail()), "e-mail versionado");
        assertFalse(state.contains("password"), "senha ausente do snapshot");
        assertFalse(state.contains("$2a$"), "hash BCrypt ausente");

        student.setLoginAttempts(3);
        users.save(student);
        assertEquals(1, javers.findSnapshots(QueryBuilder.byInstanceId(id, Student.class).build()).size(),
                "loginAttempts é ignorado pelo Javers");

        student.setName("Novo Nome");
        users.save(student);
        assertEquals(2, javers.findSnapshots(QueryBuilder.byInstanceId(id, Student.class).build()).size());
    }

    @Test
    @DisplayName("Baseline registra usuário sem histórico e é idempotente")
    void baselineIsIdempotent() {
        Student student = newStudent();
        id = student.getId();
        long before = javers.findSnapshots(QueryBuilder.byInstanceId(id, Student.class).build()).size();

        baseline.run();
        baseline.run();

        assertEquals(before, javers.findSnapshots(QueryBuilder.byInstanceId(id, Student.class).build()).size());
    }
}
