package com.n0hana.echoes_server.infra.javers;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.model.Student;

/** Prova que as chaves estrangeiras existem: apagar fora de ordem falha, por isso o pruner segue a ordem. */
@SpringBootTest
@ActiveProfiles("test")
class JaversPrunerFkSafetyTests {

    @Autowired private UserRepository users;
    @Autowired private JdbcTemplate jdbc;

    private UUID userId;

    @AfterEach
    void cleanup() {
        if (userId != null)
            users.findById(userId).ifPresent(users::delete);
    }

    @Test
    @DisplayName("Apagar jv_commit ou jv_global_id ainda referenciados por snapshot viola a FK")
    void deletingOutOfOrderViolatesForeignKeys() {
        Student s = JaversTestSupport.studentWithSnapshots(users, 2);
        userId = s.getId();
        Long gid = JaversTestSupport.globalId(jdbc, userId);
        Long commit = jdbc.queryForObject("select min(commit_fk) from jv_snapshot where global_id_fk = ?", Long.class, gid);

        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("delete from jv_commit where commit_pk = ?", commit));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("delete from jv_global_id where global_id_pk = ?", gid));
    }
}
