package com.n0hana.echoes_server.infra.javers.rollback;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.javers.core.Javers;
import org.javers.core.commit.CommitId;
import org.javers.core.metamodel.object.CdoSnapshot;
import org.javers.core.metamodel.object.InstanceId;
import org.javers.core.metamodel.object.SnapshotType;
import org.javers.repository.jql.QueryBuilder;
import org.javers.shadow.Shadow;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.javers.core.metamodel.annotation.DiffIgnore;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.n0hana.echoes_server.classroom.ClassroomMember;
import com.n0hana.echoes_server.classroom.ClassroomMemberRepository;
import com.n0hana.echoes_server.classroom.ClassroomModel;
import com.n0hana.echoes_server.device.DeviceModel;
import com.n0hana.echoes_server.device.DeviceRepository;
import com.n0hana.echoes_server.device.DeviceStatusChanged;
import com.n0hana.echoes_server.infra.file.FileStorageService;
import com.n0hana.echoes_server.infra.javers.checkpoint.JaversCheckpoint;
import com.n0hana.echoes_server.infra.javers.checkpoint.JaversCheckpointService;
import com.n0hana.echoes_server.infra.javers.retention.JaversRetentionPolicyRepository;
import com.n0hana.echoes_server.infra.javers.retention.JaversTypeResolver;
import com.n0hana.echoes_server.infra.logs.Auditable;
import com.n0hana.echoes_server.infra.notifier.NotifierService;
import com.n0hana.echoes_server.institution.InstitutionModel;
import com.n0hana.echoes_server.institution.InstitutionVerificationStatus;
import com.n0hana.echoes_server.scenario.ScenarioModel;
import com.n0hana.echoes_server.user.event.UserRestoredEvent;
import com.n0hana.echoes_server.user.model.Admin;
import com.n0hana.echoes_server.user.model.Manager;
import com.n0hana.echoes_server.user.model.Student;
import com.n0hana.echoes_server.user.model.Teacher;
import com.n0hana.echoes_server.user.model.User;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Id;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Version;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Restaura uma entidade ao estado que ela tinha no commit de um checkpoint aprovado. Copia os valores do
 * shadow para a entidade gerenciada (nunca salva o shadow) e aplica os efeitos colaterais de cada tipo.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JaversRollbackService {

    static final String DEVICE_ACTION = "POST /api/v1/admin/devices/{id}/revoke-tokens para cada id em affectedDevices";

    private final Javers javers;
    private final JaversCheckpointService checkpoints;
    private final JdbcTemplate jdbc;
    private final JaversTypeResolver types;
    private final EntityManagerFactory entityManagerFactory;
    private final DeviceRepository devices;
    private final ClassroomMemberRepository members;
    private final FileStorageService files;
    private final ApplicationEventPublisher events;
    private final NotifierService notifier;
    private final JaversRetentionPolicyRepository policies;

    @PersistenceContext
    private EntityManager em;

    @Transactional
    @Auditable(action = "JAVERS_ROLLBACK", entity = "Javers", entityId = "#entityId")
    public RollbackResult rollbackEntity(UUID checkpointId, Class<?> type, UUID entityId) {
        JaversCheckpoint checkpoint = checkpoints.requireApproved(checkpointId);
        Class<?> concrete = concreteClass(type, entityId);

        Object managed = em.find(concrete, entityId);
        boolean classroomUndeleted = false;
        if (managed == null && concrete == ClassroomModel.class) {
            classroomUndeleted = true;
            // @SQLRestriction esconde a turma excluída: desfaz a exclusão lógica antes de restaurar
            em.createNativeQuery("UPDATE classroom_model SET deleted = false WHERE id = :id")
                    .setParameter("id", entityId).executeUpdate();
            em.clear();
            managed = em.find(concrete, entityId);
        }

        if (concrete == ClassroomMember.class) {
            // o shadow de ClassroomMember não é instanciável (user é abstrato): restaura pelo snapshot
            CdoSnapshot snapshot = snapshotAt(concrete, entityId, checkpoint.getCommitId());
            ClassroomMember member = applyMemberSnapshot(snapshot, managed == null ? new ClassroomMember() : (ClassroomMember) managed);
            javers.commit("rollback:" + checkpoint.getApprovedBy(), member);
            em.flush();
            log.info("Rollback aplicado: ClassroomMember {} restaurado ao checkpoint {}", entityId, checkpointId);
            return new RollbackResult(entityId, concrete.getName(), Instant.now(), List.of(), null);
        }

        Object shadow = shadowAt(concrete, entityId, checkpoint.getCommitId());
        if (managed == null)
            throw new RollbackNotAvailableException("A entidade foi removida e este tipo não permite recriação");

        boolean wasActive = managed instanceof User user && user.isActive();
        boolean institutionWasActive = managed instanceof InstitutionModel institution && institution.isActive();
        boolean classroomWasDeleted = classroomUndeleted || (managed instanceof ClassroomModel classroom && classroom.isDeleted());

        copyState(shadow, managed);

        RollbackResult result = applyBusinessEffects(managed, entityId, concrete, wasActive, institutionWasActive,
                classroomWasDeleted);

        javers.commit("rollback:" + checkpoint.getApprovedBy(), managed);
        em.flush();
        log.info("Rollback aplicado: {} {} restaurado ao checkpoint {}", concrete.getSimpleName(), entityId, checkpointId);
        return result;
    }

    /** Restaura todas as entidades do checkpoint na mesma transação e o marca como executado. */
    @Transactional
    public List<RollbackResult> rollbackCheckpoint(UUID checkpointId) {
        JaversCheckpoint checkpoint = checkpoints.requireApproved(checkpointId);
        Class<?> type = classForName(checkpoint.getEntityType());

        List<RollbackResult> results = new java.util.ArrayList<>();
        for (String id : checkpoint.getEntityIds().split(","))
            results.add(rollbackEntity(checkpointId, type, UUID.fromString(id.trim())));

        checkpoints.markExecuted(checkpointId);
        return results;
    }

    // ---------- resolução de tipo e shadow ----------

    /** Aceita o nome completo da classe ou só o nome simples, desde que exista uma política para ele. */
    public Class<?> resolveType(String name) {
        if (name == null || name.isBlank())
            throw new IllegalStateException("entityType é obrigatório");

        String fqcn = policies.findAll().stream()
                .map(policy -> policy.getEntityType())
                .filter(type -> type.equals(name) || type.endsWith("." + name))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Tipo sem política de retenção: " + name));
        return classForName(fqcn);
    }

    /** O Javers grava a classe concreta (ex.: Student para User); descobre qual delas tem esse id. */
    public Class<?> concreteClass(Class<?> type, UUID entityId) {
        List<String> names = types.concreteTypeNames(type.getName());
        String placeholders = String.join(",", names.stream().map(n -> "?").toList());
        List<Object> params = new java.util.ArrayList<>();
        params.add("\"" + entityId + "\"");
        params.addAll(names);

        List<String> found = jdbc.queryForList(
                "SELECT type_name FROM jv_global_id WHERE local_id = ? AND type_name IN (" + placeholders + ")",
                String.class, params.toArray());
        if (found.isEmpty())
            throw new RollbackNotAvailableException("Não há histórico para " + type.getSimpleName() + " " + entityId);
        return classForName(found.get(0));
    }

    private CdoSnapshot snapshotAt(Class<?> concrete, UUID entityId, String commitId) {
        List<CdoSnapshot> snapshots = javers.findSnapshots(QueryBuilder
                .byInstanceId(entityId, concrete)
                .toCommitId(CommitId.valueOf(commitId))
                .limit(1)
                .build());
        if (snapshots.isEmpty())
            throw new RollbackNotAvailableException("Não há estado anterior ao checkpoint para " + entityId);
        return snapshots.get(0);
    }

    private Object shadowAt(Class<?> concrete, UUID entityId, String commitId) {
        List<Shadow<Object>> shadows = javers.findShadows(QueryBuilder
                .byInstanceId(entityId, concrete)
                .toCommitId(CommitId.valueOf(commitId))
                .limit(1)
                .build());
        if (shadows.isEmpty())
            throw new RollbackNotAvailableException("Não há estado anterior ao checkpoint para " + entityId);
        return shadows.get(0).get();
    }

    private static Class<?> classForName(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            throw new RollbackNotAvailableException("Tipo desconhecido: " + name);
        }
    }

    // ---------- cópia de estado ----------

    /** Copia os valores do shadow para a entidade gerenciada; referências viram referências gerenciadas. */
    void copyState(Object shadow, Object managed) {
        for (Class<?> c = managed.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (skip(field))
                    continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(shadow);
                    if (value != null && field.getType().isAnnotationPresent(Entity.class)) {
                        Object id = entityManagerFactory.getPersistenceUnitUtil().getIdentifier(value);
                        value = id == null ? null : em.getReference(field.getType(), id);
                    }
                    field.set(managed, value);
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException("Falha ao restaurar o campo " + field.getName(), e);
                }
            }
        }
    }

    private static boolean skip(Field field) {
        int modifiers = field.getModifiers();
        return Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers) || Modifier.isFinal(modifiers)
                || field.isAnnotationPresent(Id.class)
                || field.isAnnotationPresent(Version.class)
                || field.isAnnotationPresent(DiffIgnore.class)
                || field.isAnnotationPresent(CreationTimestamp.class)
                || field.isAnnotationPresent(UpdateTimestamp.class)
                || Collection.class.isAssignableFrom(field.getType())
                || Map.class.isAssignableFrom(field.getType());
    }

    // ---------- efeitos por tipo ----------

    private RollbackResult applyBusinessEffects(Object entity, UUID entityId, Class<?> type, boolean userWasActive,
            boolean institutionWasActive, boolean classroomWasDeleted) {
        Instant now = Instant.now();
        List<UUID> affectedDevices = List.of();
        String manualAction = null;

        if (entity instanceof User user) {
            // restaurar um usuário nunca restaura a senha e sempre derruba as sessões (S2)
            user.setPasswordChangedAt(now);
            user.setTokensRevokedAt(now);
            log.info("Rollback de User {}: passwordChangedAt e tokensRevokedAt atualizados; senha preservada", entityId);

            if (!userWasActive && user.isActive())
                notifyRestored(user, now);

            affectedDevices = devicesOf(user);
            manualAction = DEVICE_ACTION;
            log.warn("Rollback de User: tokens de dispositivo não revogados automaticamente; use o endpoint manual.");
        } else if (entity instanceof InstitutionModel institution) {
            if (!institutionWasActive && institution.isActive()) {
                institution.setActive(false);
                institution.setVerificationStatus(InstitutionVerificationStatus.PENDING_VERIFICATION);
                log.warn("Rollback de Institution {}: não reativada automaticamente; segue pendente de aprovação (RF21)", entityId);
            }
        } else if (entity instanceof DeviceModel device) {
            events.publishEvent(new DeviceStatusChanged(device.getId(), device.getStatus()));
            log.info("Rollback de Device {}: DeviceStatusChanged publicado com status {}", entityId, device.getStatus());
        } else if (entity instanceof ClassroomModel classroom) {
            if (classroomWasDeleted && !classroom.isDeleted()) {
                int restored = restoreMembers(classroom);
                log.info("Rollback de Classroom {}: turma restaurada e {} membro(s) recriado(s)", entityId, restored);
            }
        } else if (entity instanceof ScenarioModel scenario) {
            String audio = scenario.getAudioUrl();
            if (audio == null || audio.isBlank() || !java.nio.file.Files.exists(files.load(audio)))
                throw new RollbackNotAvailableException("arquivo de áudio não existe mais; reupar antes de restaurar");
            log.info("Rollback de Scenario {}: arquivo de áudio {} conferido", entityId, audio);
        }

        return new RollbackResult(entityId, type.getName(), now, affectedDevices, manualAction);
    }

    private void notifyRestored(User user, Instant now) {
        events.publishEvent(new UserRestoredEvent(user.getId(), now));
        String email = user.getEmail();
        if (email == null || email.endsWith("@anon.invalid") || email.endsWith("@deleted.local")) {
            log.warn("Rollback de User {}: e-mail anonimizado, notificação não enviada", user.getId());
            return;
        }
        notifier.sendAccountRestored(email, user.getName());
        log.info("Rollback de User {}: UserRestoredEvent publicado e e-mail de restauração enviado", user.getId());
    }

    private List<UUID> devicesOf(User user) {
        InstitutionModel institution = null;
        if (user instanceof Manager m) institution = m.getInstitution();
        else if (user instanceof Teacher t) institution = t.getInstitution();
        else if (user instanceof Student s) institution = s.getInstitution();
        else if (user instanceof Admin) institution = null;

        if (institution == null || institution.getId() == null)
            return List.of();
        return devices.findByInstitutionId(institution.getId()).stream().map(DeviceModel::getId).toList();
    }

    // ---------- membros de turma ----------

    /** Recria os membros cuja linha foi removida, a partir do último estado vivo no histórico. */
    private int restoreMembers(ClassroomModel classroom) {
        List<String> candidates = jdbc.queryForList(
                "SELECT DISTINCT g.local_id FROM jv_snapshot s JOIN jv_global_id g ON g.global_id_pk = s.global_id_fk "
                        + "WHERE g.type_name = ? AND s.state LIKE ?",
                String.class, ClassroomMember.class.getName(), "%" + classroom.getId() + "%");

        int restored = 0;
        for (String local : candidates) {
            UUID memberId = UUID.fromString(local.replace("\"", ""));
            if (members.existsById(memberId))
                continue;

            CdoSnapshot snapshot = lastLiveSnapshot(memberId);
            if (snapshot == null)
                continue;
            applyMemberSnapshot(snapshot, new ClassroomMember());
            restored++;
        }
        return restored;
    }

    /** último snapshot do membro em que ele ainda existia (ignora o snapshot de exclusão) */
    private CdoSnapshot lastLiveSnapshot(UUID memberId) {
        for (SnapshotType snapshotType : new SnapshotType[] { SnapshotType.UPDATE, SnapshotType.INITIAL }) {
            List<CdoSnapshot> snapshots = javers.findSnapshots(QueryBuilder
                    .byInstanceId(memberId, ClassroomMember.class)
                    .withSnapshotType(snapshotType)
                    .limit(1).build());
            if (!snapshots.isEmpty())
                return snapshots.get(0);
        }
        return null;
    }

    private ClassroomMember applyMemberSnapshot(CdoSnapshot snapshot, ClassroomMember member) {
        member.setClassroom(em.getReference(ClassroomModel.class, referenceId(snapshot.getPropertyValue("classroom"))));
        member.setUser(em.getReference(User.class, referenceId(snapshot.getPropertyValue("user"))));
        Object studentId = snapshot.getPropertyValue("studentId");
        member.setStudentId(studentId == null ? null : UUID.fromString(String.valueOf(studentId)));
        if (member.getId() == null)
            em.persist(member);
        return member;
    }

    private static UUID referenceId(Object reference) {
        if (reference instanceof InstanceId instanceId)
            return UUID.fromString(String.valueOf(instanceId.getCdoId()));
        throw new RollbackNotAvailableException("Referência ausente no histórico do membro");
    }
}
