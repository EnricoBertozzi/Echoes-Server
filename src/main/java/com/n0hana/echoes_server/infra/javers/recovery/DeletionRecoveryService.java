package com.n0hana.echoes_server.infra.javers.recovery;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.javers.core.Javers;
import org.javers.core.commit.CommitId;
import org.javers.core.metamodel.object.InstanceId;
import org.javers.core.metamodel.object.SnapshotType;
import org.javers.repository.jql.QueryBuilder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.n0hana.echoes_server.classroom.ClassroomMember;
import com.n0hana.echoes_server.classroom.ClassroomMemberRepository;
import com.n0hana.echoes_server.classroom.ClassroomModel;
import com.n0hana.echoes_server.classroom.ClassroomRepository;
import com.n0hana.echoes_server.classroom.exception.ClassroomNotFoundException;
import com.n0hana.echoes_server.infra.javers.recovery.DeletionRecovery.Status;
import com.n0hana.echoes_server.infra.javers.recovery.DeletionRecovery.Type;
import com.n0hana.echoes_server.infra.javers.recovery.DeletionRecoveryTargets.Summary;
import com.n0hana.echoes_server.infra.javers.recovery.DeletionRecoveryTargets.Target;
import com.n0hana.echoes_server.infra.logs.Auditable;
import com.n0hana.echoes_server.institution.InstitutionModel;
import com.n0hana.echoes_server.institution.InstitutionRepository;
import com.n0hana.echoes_server.institution.InstitutionStatusChanged;
import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.model.Student;
import com.n0hana.echoes_server.user.model.Teacher;
import com.n0hana.echoes_server.user.model.User;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DeletionRecoveryService {

    public record SkippedMember(UUID membershipId, String reason) {}
    public record Detail(Summary target, boolean canApprove, boolean canExecute, String blockedReason,
            int recoverableMembers, List<SkippedMember> skippedMembers, Instant restoredAt) {}
    public record Result(Instant restoredAt, int recoveredMembers, List<SkippedMember> skippedMembers,
            @JsonIgnore String targetName) {}

    private static final String INSTITUTION_ID = "#type.name() == 'INSTITUTION' ? #id : null";

    private record Roster(List<User> users, List<SkippedMember> skipped) {}

    private final DeletionRecoveryRepository cases;
    private final DeletionRecoveryTargets targets;
    private final ClassroomRepository classrooms;
    private final ClassroomMemberRepository members;
    private final InstitutionRepository institutions;
    private final UserRepository users;
    private final Javers javers;
    private final ApplicationEventPublisher events;

    @Transactional(propagation = Propagation.MANDATORY)
    public List<ClassroomMember> recordClassroomDeletion(ClassroomModel classroom) {
        lockActiveClassroom(classroom.getId());
        var recovery = resetCase(Type.CLASSROOM, classroom.getId());
        List<ClassroomMember> roster = members.findRosterForDeletion(classroom.getId());
        String author = "classroom-before-delete";
        String commitId = javers.commit(author, classroom).getId().value();
        for (ClassroomMember member : roster)
            commitId = javers.commit(author, member).getId().value();
        recovery.setCommitId(commitId);
        recovery.setMemberIds(roster.stream().map(member -> member.getId().toString()).collect(Collectors.joining(",")));
        cases.save(recovery);
        return roster;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockActiveClassroom(UUID classroomId) {
        targets.find(Type.CLASSROOM, classroomId, true).filter(target -> !target.deleted())
                .orElseThrow(ClassroomNotFoundException::new);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordInstitutionDeletion(InstitutionModel institution) {
        requireCurrentTarget(Type.INSTITUTION, institution.getId(), false);
        var recovery = resetCase(Type.INSTITUTION, institution.getId());
        recovery.setCommitId(javers.commit("institution-before-delete", institution).getId().value());
        cases.save(recovery);
    }

    @Transactional(readOnly = true)
    public Page<Summary> list(Type type, Status status, Pageable pageable) {
        return targets.list(type, status, pageable);
    }

    @Transactional(readOnly = true)
    public Detail detail(Type type, UUID id, UUID adminId) {
        Target target = findTarget(type, id, false);
        var recovery = findCase(type, id);
        if (!target.deleted() && recovery.getStatus() != Status.EXECUTED)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Registro excluído não encontrado");
        return describe(target, recovery, adminId);
    }

    @Transactional
    @Auditable(action = "DELETION_RESTORE_REQUESTED", entity = "DeletionRecovery", entityId = "#id",
            entityName = "#result", institutionId = INSTITUTION_ID)
    public String request(Type type, UUID id, UUID adminId) {
        Target target = requireCurrentTarget(type, id, true);
        var recovery = findCase(type, id);
        requireStatus(recovery, Status.AVAILABLE, "Este registro já tem uma solicitação de restauração");
        recovery.setStatus(Status.PENDING_APPROVAL);
        recovery.setRequestedBy(adminId);
        cases.save(recovery);
        return target.name();
    }

    @Transactional
    @Auditable(action = "DELETION_RESTORE_APPROVED", entity = "DeletionRecovery", entityId = "#id",
            entityName = "#result", institutionId = INSTITUTION_ID)
    public String approve(Type type, UUID id, UUID adminId) {
        Target target = requireCurrentTarget(type, id, true);
        var recovery = findCase(type, id);
        requireStatus(recovery, Status.PENDING_APPROVAL, "A restauração não está aguardando aprovação");
        if (adminId.equals(recovery.getRequestedBy()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Outro administrador precisa aprovar a restauração");
        requireUnblocked(target);
        recovery.setStatus(Status.APPROVED);
        recovery.setApprovedBy(adminId);
        return target.name();
    }

    @Transactional
    @Auditable(action = "DELETION_RESTORED", entity = "DeletionRecovery", entityId = "#id",
            entityName = "#result.targetName()", institutionId = INSTITUTION_ID,
            details = "'Tipo: ' + #type + '; vínculos recuperados: ' + #result.recoveredMembers()"
                    + " + '; vínculos ignorados: ' + #result.skippedMembers().size()")
    public Result execute(Type type, UUID id) {
        Target target = requireCurrentTarget(type, id, true);
        var recovery = findCase(type, id);
        requireStatus(recovery, Status.APPROVED, "A restauração precisa estar aprovada");
        if (type == Type.CLASSROOM) {
            if (institutionUnavailable(target, true))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Restaure primeiro a instituição desta turma.");
        } else {
            requireUnblocked(target);
        }
        Roster roster = type == Type.CLASSROOM ? resolveRoster(target, recovery) : new Roster(List.of(), List.of());
        targets.restore(target);
        int recoveredMembers;
        if (type == Type.CLASSROOM) {
            var classroom = classrooms.findById(id).orElseThrow();
            List<ClassroomMember> restored = roster.users().stream()
                    .map(user -> ClassroomMember.builder().classroom(classroom).user(user)
                            .studentId(user instanceof Student ? user.getId() : null).build())
                    .toList();
            members.saveAll(restored);
            members.flush();
            javers.commit("classroom-restored", classroom);
            recoveredMembers = restored.size();
        } else {
            var institution = institutions.findById(id).orElseThrow();
            javers.commit("institution-restored", institution);
            events.publishEvent(new InstitutionStatusChanged(id, institution.isActive(), false));
            recoveredMembers = targets.institutionMemberCount(id);
        }
        recovery.setStatus(Status.EXECUTED);
        recovery.setRestoredAt(Instant.now());
        return new Result(recovery.getRestoredAt(), recoveredMembers, roster.skipped(), target.name());
    }

    private Detail describe(Target target, DeletionRecovery recovery, UUID adminId) {
        boolean rosterRecorded = recovery.getMemberIds() != null;
        String blocked = target.deleted() ? blockedReason(target) : null;
        Status status = recovery.getStatus();
        boolean canApprove = status == Status.PENDING_APPROVAL && !adminId.equals(recovery.getRequestedBy()) && blocked == null;
        boolean canExecute = status == Status.APPROVED && blocked == null;
        var summary = new Summary(target.type(), target.id(), target.name(), recovery.getDeletedAt(), status, rosterRecorded);
        if (!target.deleted())
            return new Detail(summary, false, false, null, 0, List.of(), recovery.getRestoredAt());
        if (target.type() == Type.INSTITUTION)
            return new Detail(summary, canApprove, canExecute, blocked,
                    targets.institutionMemberCount(target.id()), List.of(), null);
        Roster roster = blocked == null ? resolveRoster(target, recovery) : new Roster(List.of(), List.of());
        return new Detail(summary, canApprove, canExecute, blocked, roster.users().size(), roster.skipped(), null);
    }

    private Roster resolveRoster(Target target, DeletionRecovery recovery) {
        List<User> eligible = new ArrayList<>();
        List<SkippedMember> skipped = new ArrayList<>();
        for (UUID membershipId : recovery.memberIds()) {
            var snapshots = javers.findSnapshots(QueryBuilder.byInstanceId(membershipId, ClassroomMember.class)
                    .toCommitId(CommitId.valueOf(recovery.getCommitId())).limit(1).build());
            if (snapshots.isEmpty() || snapshots.getFirst().getType() == SnapshotType.TERMINAL
                    || !(snapshots.getFirst().getPropertyValue("user") instanceof InstanceId reference)) {
                skipped.add(new SkippedMember(membershipId, "O vínculo não está mais disponível no histórico."));
                continue;
            }
            UUID userId = UUID.fromString(String.valueOf(reference.getCdoId()));
            User user = users.findById(userId).orElse(null);
            String reason = membershipConflict(target, user);
            if (reason == null)
                eligible.add(user);
            else
                skipped.add(new SkippedMember(membershipId, reason));
        }
        return new Roster(eligible, skipped);
    }

    private String membershipConflict(Target target, User user) {
        if (user == null || user.getEmail().endsWith("@deleted.local") || user.getEmail().endsWith("@anon.invalid"))
            return "A conta foi removida.";
        InstitutionModel institution = switch (user) {
            case Student student -> student.getInstitution();
            case Teacher teacher -> teacher.getInstitution();
            default -> null;
        };
        if (institution == null || !target.institutionId().equals(institution.getId()))
            return "A conta não está mais vinculada à instituição da turma.";
        if (members.existsByClassroomIdAndUserId(target.id(), user.getId()))
            return "O vínculo já existe.";
        if (user instanceof Student && members.existsByUserId(user.getId()))
            return "O aluno já está matriculado em outra turma.";
        return null;
    }

    private String blockedReason(Target target) {
        if (target.type() == Type.CLASSROOM && institutionUnavailable(target, false))
            return "Restaure primeiro a instituição desta turma.";
        if (target.type() == Type.INSTITUTION && targets.institutionIdentityTaken(target))
            return "Outra instituição já usa o mesmo CNPJ ou e-mail.";
        return null;
    }

    private boolean institutionUnavailable(Target classroom, boolean lock) {
        return targets.find(Type.INSTITUTION, classroom.institutionId(), lock).filter(parent -> !parent.deleted()).isEmpty();
    }

    private void requireUnblocked(Target target) {
        String reason = blockedReason(target);
        if (reason != null)
            throw new ResponseStatusException(HttpStatus.CONFLICT, reason);
    }

    private Target requireCurrentTarget(Type type, UUID id, boolean deleted) {
        Target target = findTarget(type, id, true);
        if (target.deleted() != deleted)
            throw new ResponseStatusException(HttpStatus.CONFLICT, deleted
                    ? "O registro já está disponível." : "O registro já está excluído.");
        return target;
    }

    private Target findTarget(Type type, UUID id, boolean lock) {
        return targets.find(type, id, lock)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Registro não encontrado"));
    }

    private DeletionRecovery findCase(Type type, UUID id) {
        return cases.findByTypeAndTargetId(type, id).orElseGet(() -> {
            var recovery = new DeletionRecovery();
            recovery.setType(type);
            recovery.setTargetId(id);
            return recovery;
        });
    }

    private DeletionRecovery resetCase(Type type, UUID id) {
        var recovery = findCase(type, id);
        recovery.setStatus(Status.AVAILABLE);
        recovery.setDeletedAt(Instant.now());
        recovery.setRequestedBy(null);
        recovery.setApprovedBy(null);
        recovery.setRestoredAt(null);
        return recovery;
    }

    private static void requireStatus(DeletionRecovery recovery, Status status, String message) {
        if (recovery.getStatus() != status)
            throw new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
