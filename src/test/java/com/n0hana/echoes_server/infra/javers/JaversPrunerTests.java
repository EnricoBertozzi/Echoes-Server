package com.n0hana.echoes_server.infra.javers;

import static com.n0hana.echoes_server.infra.javers.JaversTestSupport.USER_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.n0hana.echoes_server.infra.javers.retention.JaversPruner;
import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.model.Student;

@SpringBootTest
@ActiveProfiles("test")
class JaversPrunerTests {

    @Autowired private JaversPruner pruner;
    @Autowired private UserRepository users;
    @Autowired private JdbcTemplate jdbc;

    private UUID userId;

    @AfterEach
    void cleanup() {
        RequestContextHolder.resetRequestAttributes();
        if (userId != null)
            users.findById(userId).ifPresent(users::delete);
    }

    private Instant cutoff() {
        return Instant.now().minus(Duration.ofDays(365));
    }

    private Instant old() {
        return Instant.now().minus(Duration.ofDays(900));
    }

    @Test
    @DisplayName("20 snapshots antigos, manter 5: sobram os 5 mais recentes e o jv_global_id permanece")
    void keepsTheMostRecentSnapshots() {
        Student s = JaversTestSupport.studentWithSnapshots(users, 20);
        userId = s.getId();
        Long gid = JaversTestSupport.globalId(jdbc, userId);
        List<Long> all = JaversTestSupport.snapshotPks(jdbc, gid);
        assertEquals(20, all.size());
        JaversTestSupport.setCommitDate(jdbc, all, old());

        int removed = pruner.pruneEntity(USER_TYPE, cutoff(), 5);

        assertTrue(removed >= 15, "removidos: " + removed);
        assertEquals(all.subList(15, 20), JaversTestSupport.snapshotPks(jdbc, gid), "ficam os 5 mais novos");
        assertEquals(1, jdbc.queryForObject("select count(*) from jv_global_id where global_id_pk = ?", Integer.class, gid),
                "jv_global_id de entidade viva não é apagado");
    }

    @Test
    @DisplayName("Snapshots mais novos que o corte não são podados, mesmo acima do mínimo")
    void recentSnapshotsAreNeverPruned() {
        Student s = JaversTestSupport.studentWithSnapshots(users, 20);
        userId = s.getId();
        Long gid = JaversTestSupport.globalId(jdbc, userId);
        List<Long> all = JaversTestSupport.snapshotPks(jdbc, gid);
        JaversTestSupport.setCommitDate(jdbc, all.subList(0, 12), old());

        pruner.pruneEntity(USER_TYPE, cutoff(), 10);

        // dos 12 antigos, só os que têm 10 ou mais snapshots mais novos são removidos (os 10 primeiros)
        assertEquals(all.subList(10, 20), JaversTestSupport.snapshotPks(jdbc, gid));
    }

    @Test
    @DisplayName("Poda a pedido da política de User alcança a classe concreta (Student)")
    void resolvesConcreteSubclass() {
        Student s = JaversTestSupport.studentWithSnapshots(users, 8);
        userId = s.getId();
        Long gid = JaversTestSupport.globalId(jdbc, userId);
        JaversTestSupport.setCommitDate(jdbc, JaversTestSupport.snapshotPks(jdbc, gid), old());

        pruner.pruneEntity(USER_TYPE, cutoff(), 3);

        assertEquals(3, JaversTestSupport.count(jdbc, gid));
    }

    @Test
    @DisplayName("Parâmetros inválidos: data nula, futura, mínimo negativo ou tipo vazio")
    void rejectsInvalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> pruner.pruneEntity(USER_TYPE, null, 5));
        assertThrows(IllegalArgumentException.class, () -> pruner.pruneEntity(USER_TYPE, Instant.now().plusSeconds(60), 5));
        assertThrows(IllegalArgumentException.class, () -> pruner.pruneEntity(USER_TYPE, cutoff(), -1));
        assertThrows(IllegalArgumentException.class, () -> pruner.pruneEntity(" ", cutoff(), 5));
    }
}
