package com.n0hana.echoes_server.infra.javers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.n0hana.echoes_server.infra.javers.anonymization.JaversAnonymizationService;
import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.model.Student;

@SpringBootTest
@ActiveProfiles("test")
class JaversAnonymizationServiceTests {

    private static final String BCRYPT = "$2a$10$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZ01234";

    @Autowired private JaversAnonymizationService service;
    @Autowired private UserRepository users;
    @Autowired private JdbcTemplate jdbc;

    private UUID id;
    private UUID otherId;

    @AfterEach
    void cleanup() {
        for (UUID u : new UUID[] { id, otherId })
            if (u != null)
                users.findById(u).ifPresent(users::delete);
    }

    private Student student(String email, String name) {
        Student s = new Student();
        s.setEmail(email);
        s.setName(name);
        s.setPassword(BCRYPT);
        return users.save(s);
    }

    private List<String> states(UUID userId) {
        return jdbc.queryForList("select s.state from jv_snapshot s join jv_global_id g on g.global_id_pk = s.global_id_fk where g.local_id = ?",
                String.class, "\"" + userId + "\"");
    }

    private void awaitAnonymized(UUID userId, String original) throws Exception {
        for (int i = 0; i < 100; i++) {
            if (states(userId).stream().noneMatch(st -> st.contains(original)))
                return;
            Thread.sleep(100);
        }
    }

    @Test
    @DisplayName("Depois da exclusão lógica nenhum snapshot guarda o e-mail original, o nome nem o hash BCrypt; outros usuários ficam intactos")
    void removesPersonalDataFromAllSnapshots() throws Exception {
        String email = "original-" + UUID.randomUUID() + "@test.local";
        Student s = student(email, "Maria da Silva");
        id = s.getId();
        s.setName("Maria S. Silva");
        users.save(s);
        Student other = student("outro-" + UUID.randomUUID() + "@test.local", "Outra Pessoa");
        otherId = other.getId();
        assertTrue(states(id).stream().anyMatch(st -> st.contains(email)), "pré-condição: e-mail no histórico");

        // o que UserManagerService.softDelete faz
        s.setActive(false);
        s.setName(null);
        s.setEmail("deleted_user_" + UUID.randomUUID() + "@deleted.local");
        s.setPassword(null);
        users.save(s);

        service.anonymizeAfterSoftDelete(id);
        awaitAnonymized(id, email);

        List<String> after = states(id);
        assertFalse(after.isEmpty());
        for (String state : after) {
            assertFalse(state.contains(email), "e-mail original: " + state);
            assertFalse(state.contains("Maria"), "nome original: " + state);
            assertFalse(state.contains("$2a$"), "hash BCrypt: " + state);
            assertTrue(state.contains("deleted_user_" + id + "@anon.invalid") || !state.contains("\"email\""));
        }
        assertTrue(after.stream().anyMatch(st -> st.contains("Anonimizado")));

        List<String> others = states(otherId);
        assertTrue(others.stream().anyMatch(st -> st.contains("Outra Pessoa")), "outro usuário não pode ser alterado");
    }

    @Test
    @DisplayName("Usuário inexistente é ignorado sem erro")
    void unknownUserIsIgnored() {
        service.anonymizeAfterSoftDelete(UUID.randomUUID());
    }

    @Test
    @DisplayName("anonymizeState troca e-mail e nome, ignora estados sem esses campos e rejeita JSON inválido")
    void anonymizeStateRewritesOnlyPersonalFields() {
        JaversAnonymizationService unit = new JaversAnonymizationService(null, null, null, null);

        String result = unit.anonymizeState("{\"id\":\"1\",\"email\":\"a@b.com\",\"name\":\"Ana\",\"active\":true}", "x@anon.invalid");
        assertTrue(result.contains("x@anon.invalid"));
        assertTrue(result.contains("Anonimizado"));
        assertFalse(result.contains("a@b.com"));
        assertTrue(result.contains("\"active\":true"));

        assertTrue(unit.anonymizeState("{\"id\":\"1\",\"email\":\"a@b.com\",\"name\":null}", "x").contains("\"name\":null"));
        org.junit.jupiter.api.Assertions.assertNull(unit.anonymizeState("{\"id\":\"1\",\"active\":true}", "x"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> unit.anonymizeState("não é json", "x"));
    }
}
