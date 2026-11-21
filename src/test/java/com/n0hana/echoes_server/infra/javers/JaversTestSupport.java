package com.n0hana.echoes_server.infra.javers;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.model.Student;

/** Cria histórico de um usuário e envelhece commits, para os testes de poda. */
final class JaversTestSupport {

    static final String USER_TYPE = "com.n0hana.echoes_server.user.model.User";

    private JaversTestSupport() {
    }

    /** salva um aluno e altera o nome até ter {@code snapshots} snapshots */
    static Student studentWithSnapshots(UserRepository users, int snapshots) {
        Student s = new Student();
        s.setEmail("jv-" + UUID.randomUUID() + "@test.local");
        s.setName("v1");
        s.setPassword("hash");
        s = users.save(s);
        for (int i = 2; i <= snapshots; i++) {
            s.setName("v" + i);
            s = users.save(s);
        }
        return s;
    }

    static Long globalId(JdbcTemplate jdbc, UUID id) {
        return jdbc.queryForObject("select global_id_pk from jv_global_id where local_id = ?", Long.class, "\"" + id + "\"");
    }

    /** snapshot_pk do mais antigo para o mais novo */
    static List<Long> snapshotPks(JdbcTemplate jdbc, Long globalId) {
        return jdbc.queryForList("select snapshot_pk from jv_snapshot where global_id_fk = ? order by snapshot_pk", Long.class, globalId);
    }

    static int count(JdbcTemplate jdbc, Long globalId) {
        return snapshotPks(jdbc, globalId).size();
    }

    /** define a data do commit de cada snapshot informado */
    static void setCommitDate(JdbcTemplate jdbc, List<Long> snapshotPks, Instant when) {
        for (Long pk : snapshotPks) {
            jdbc.update("update jv_commit set commit_date_instant = ? where commit_pk = (select commit_fk from jv_snapshot where snapshot_pk = ?)",
                    DateTimeFormatter.ISO_INSTANT.format(when), pk);
        }
    }
}
