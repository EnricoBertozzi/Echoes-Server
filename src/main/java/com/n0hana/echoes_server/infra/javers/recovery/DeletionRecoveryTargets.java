package com.n0hana.echoes_server.infra.javers.recovery;

import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.n0hana.echoes_server.infra.javers.recovery.DeletionRecovery.Status;
import com.n0hana.echoes_server.infra.javers.recovery.DeletionRecovery.Type;

import lombok.RequiredArgsConstructor;

/** Acesso aos registros ocultos pelo filtro de exclusão lógica do Hibernate. */
@Repository
@RequiredArgsConstructor
class DeletionRecoveryTargets {

    record Summary(Type type, UUID id, String name, Instant deletedAt, Status status, boolean rosterRecorded) {}
    record Target(Type type, UUID id, String name, boolean deleted, UUID institutionId, String cnpj, String email) {}

    private final JdbcTemplate jdbc;

    Page<Summary> list(Type type, Status status, Pageable pageable) {
        String classrooms = "SELECT 'CLASSROOM' AS target_type, id, title AS target_name, deleted FROM classroom_model";
        String institutions = "SELECT 'INSTITUTION' AS target_type, id, name AS target_name, deleted FROM institutions";
        String targets = switch (type) {
            case null -> classrooms + " UNION ALL " + institutions;
            case CLASSROOM -> classrooms;
            case INSTITUTION -> institutions;
        };
        String from = """
                 FROM (%s) targets LEFT JOIN deletion_recovery recovery
                   ON recovery.target_type = targets.target_type AND recovery.target_id = targets.id
                 WHERE (targets.deleted = TRUE OR recovery.status = 'EXECUTED')
                """.formatted(targets);
        List<Object> parameters = new ArrayList<>();
        if (status != null) {
            from += " AND COALESCE(recovery.status, 'AVAILABLE') = ?";
            parameters.add(status.name());
        }
        long count = jdbc.queryForObject("SELECT COUNT(*)" + from, Long.class, parameters.toArray());
        parameters.add(pageable.getPageSize());
        parameters.add(pageable.getOffset());
        List<Summary> rows = jdbc.query("""
                SELECT targets.target_type, targets.id, targets.target_name, recovery.deleted_at,
                       COALESCE(recovery.status, 'AVAILABLE') AS recovery_status,
                       recovery.member_ids IS NOT NULL AS roster_recorded
                """ + from + " ORDER BY recovery.deleted_at DESC, targets.target_type, targets.id LIMIT ? OFFSET ?",
                (row, index) -> new Summary(Type.valueOf(row.getString("target_type")), uuid(row, "id"),
                        row.getString("target_name"), instant(row.getTimestamp("deleted_at")),
                        Status.valueOf(row.getString("recovery_status")), row.getBoolean("roster_recorded")), parameters.toArray());
        return new PageImpl<>(rows, pageable, count);
    }

    Optional<Target> find(Type type, UUID id, boolean lock) {
        String select = switch (type) {
            case CLASSROOM -> "SELECT title AS target_name, deleted, institution_id FROM classroom_model";
            case INSTITUTION -> "SELECT name AS target_name, deleted, cnpj, email FROM institutions";
        };
        return jdbc.query(select + " WHERE id = ?" + (lock ? " FOR UPDATE" : ""),
                (row, index) -> new Target(type, id, row.getString("target_name"), row.getBoolean("deleted"),
                        type == Type.CLASSROOM ? uuid(row, "institution_id") : id,
                        type == Type.INSTITUTION ? row.getString("cnpj") : null,
                        type == Type.INSTITUTION ? row.getString("email") : null), bytes(id)).stream().findFirst();
    }

    int institutionMemberCount(UUID id) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM users WHERE institution_id = ? AND role IN ('MANAGER', 'TEACHER', 'STUDENT')
                """, Integer.class, bytes(id));
    }

    boolean institutionIdentityTaken(Target target) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM institutions WHERE deleted = FALSE AND (cnpj = ? OR email = ?)
                """, Long.class, target.cnpj(), target.email()) > 0;
    }

    void restore(Target target) {
        String update = switch (target.type()) {
            case CLASSROOM -> "UPDATE classroom_model SET deleted = FALSE WHERE id = ?";
            case INSTITUTION -> "UPDATE institutions SET deleted = FALSE, delete_token = 'ACTIVE' WHERE id = ?";
        };
        jdbc.update(update, bytes(target.id()));
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private static UUID uuid(ResultSet row, String column) throws SQLException {
        byte[] value = row.getBytes(column);
        ByteBuffer buffer = ByteBuffer.wrap(value);
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    private static byte[] bytes(UUID id) {
        return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();
    }
}
