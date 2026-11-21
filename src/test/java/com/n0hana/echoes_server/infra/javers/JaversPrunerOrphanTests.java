package com.n0hana.echoes_server.infra.javers;

import static com.n0hana.echoes_server.infra.javers.JaversTestSupport.USER_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
import org.springframework.test.context.ActiveProfiles;

import com.n0hana.echoes_server.infra.javers.retention.JaversPruner;
import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.model.Student;

@SpringBootTest
@ActiveProfiles("test")
class JaversPrunerOrphanTests {

    @Autowired private JaversPruner pruner;
    @Autowired private UserRepository users;
    @Autowired private JdbcTemplate jdbc;

    private UUID userId;

    @AfterEach
    void cleanup() {
        if (userId != null)
            users.findById(userId).ifPresent(users::delete);
    }

    @Test
    @DisplayName("Podar todos os snapshots de um global_id remove também o global_id, os commits e as propriedades órfãs")
    void removesOrphans() {
        Student s = JaversTestSupport.studentWithSnapshots(users, 4);
        userId = s.getId();
        Long gid = JaversTestSupport.globalId(jdbc, userId);
        List<Long> snapshots = JaversTestSupport.snapshotPks(jdbc, gid);
        List<Long> commits = jdbc.queryForList("select commit_fk from jv_snapshot where global_id_fk = ?", Long.class, gid);
        JaversTestSupport.setCommitDate(jdbc, snapshots, Instant.now().minus(Duration.ofDays(900)));
        jdbc.update("insert into jv_commit_property (property_name, property_value, commit_fk) values ('origem', 'teste', ?)", commits.get(0));

        pruner.pruneEntity(JaversTestSupport.USER_TYPE, Instant.now().minus(Duration.ofDays(365)), 0);

        assertEquals(0, JaversTestSupport.count(jdbc, gid));
        assertEquals(0, jdbc.queryForObject("select count(*) from jv_global_id where global_id_pk = ?", Integer.class, gid));
        for (Long commit : commits) {
            assertEquals(0, jdbc.queryForObject("select count(*) from jv_commit where commit_pk = ?", Integer.class, commit));
            assertEquals(0, jdbc.queryForObject("select count(*) from jv_commit_property where commit_fk = ?", Integer.class, commit));
        }
    }

    @Test
    @DisplayName("Commits ainda referenciados por snapshots outros não são apagados")
    void keepsReferencedCommits() {
        Student s = JaversTestSupport.studentWithSnapshots(users, 3);
        userId = s.getId();
        Long gid = JaversTestSupport.globalId(jdbc, userId);
        List<Long> commits = jdbc.queryForList("select commit_fk from jv_snapshot where global_id_fk = ?", Long.class, gid);
        JaversTestSupport.setCommitDate(jdbc, JaversTestSupport.snapshotPks(jdbc, gid), Instant.now().minus(Duration.ofDays(900)));

        pruner.pruneEntity(USER_TYPE, Instant.now().minus(Duration.ofDays(365)), 3);

        for (Long commit : commits)
            assertEquals(1, jdbc.queryForObject("select count(*) from jv_commit where commit_pk = ?", Integer.class, commit));
    }
}
