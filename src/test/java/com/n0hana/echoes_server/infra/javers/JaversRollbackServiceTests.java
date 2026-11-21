package com.n0hana.echoes_server.infra.javers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.event.EventListener;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.n0hana.echoes_server.classroom.ClassroomMember;
import com.n0hana.echoes_server.classroom.ClassroomMemberRepository;
import com.n0hana.echoes_server.classroom.ClassroomModel;
import com.n0hana.echoes_server.classroom.ClassroomRepository;
import com.n0hana.echoes_server.device.DeviceModel;
import com.n0hana.echoes_server.device.DeviceRepository;
import com.n0hana.echoes_server.device.DeviceStatus;
import com.n0hana.echoes_server.device.DeviceStatusChanged;
import com.n0hana.echoes_server.infra.file.FileStorageService;
import com.n0hana.echoes_server.infra.javers.checkpoint.JaversCheckpoint;
import com.n0hana.echoes_server.infra.javers.checkpoint.JaversCheckpointRepository;
import com.n0hana.echoes_server.infra.javers.checkpoint.JaversCheckpointService;
import com.n0hana.echoes_server.infra.javers.rollback.JaversRollbackService;
import com.n0hana.echoes_server.infra.javers.rollback.RollbackNotAvailableException;
import com.n0hana.echoes_server.infra.javers.rollback.RollbackResult;
import com.n0hana.echoes_server.infra.notifier.NotifierService;
import com.n0hana.echoes_server.institution.InstitutionModel;
import com.n0hana.echoes_server.institution.InstitutionRepository;
import com.n0hana.echoes_server.institution.InstitutionVerificationStatus;
import com.n0hana.echoes_server.scenario.ScenarioModel;
import com.n0hana.echoes_server.scenario.ScenarioRepository;
import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.event.UserRestoredEvent;
import com.n0hana.echoes_server.user.model.Student;
import com.n0hana.echoes_server.user.model.User;

@SpringBootTest(classes = { com.n0hana.echoes_server.EchoesServerApplication.class, JaversRollbackServiceTests.Events.class })
@ActiveProfiles("test")
class JaversRollbackServiceTests {

    static class Events {
        final java.util.List<Object> received = new java.util.concurrent.CopyOnWriteArrayList<>();

        @EventListener
        void on(UserRestoredEvent event) {
            received.add(event);
        }

        @EventListener
        void on(DeviceStatusChanged event) {
            received.add(event);
        }
    }

    @Autowired private JaversRollbackService rollback;
    @Autowired private JaversCheckpointService checkpoints;
    @Autowired private JaversCheckpointRepository checkpointRepository;
    @Autowired private UserRepository users;
    @Autowired private InstitutionRepository institutions;
    @Autowired private DeviceRepository devices;
    @Autowired private ClassroomRepository classrooms;
    @Autowired private ClassroomMemberRepository members;
    @Autowired private ScenarioRepository scenarios;
    @Autowired private FileStorageService files;
    @Autowired private Events events;
    @Autowired private com.n0hana.echoes_server.infra.javers.retention.JaversRetentionPolicyRepository policies;
    @MockitoBean private NotifierService notifier;

    private InstitutionModel institution;
    private final List<UUID> userIds = new java.util.ArrayList<>();
    private final List<UUID> classroomIds = new java.util.ArrayList<>();
    private final List<UUID> memberIds = new java.util.ArrayList<>();
    private final List<UUID> deviceIds = new java.util.ArrayList<>();

    @BeforeEach
    void setUp() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        events.received.clear();
        institution = newInstitution("Inst " + UUID.randomUUID());
    }

    @AfterEach
    void cleanup() throws Exception {
        RequestContextHolder.resetRequestAttributes();
        checkpointRepository.deleteAll();
        policies.deleteAll();
        // pelo id: os relacionamentos são lazy e não há sessão aberta aqui
        classroomIds.forEach(id -> members.findByClassroomId(id).forEach(members::delete));
        memberIds.forEach(id -> members.findById(id).ifPresent(members::delete));
        classroomIds.forEach(id -> jdbcDeleteClassroom(id));
        deviceIds.forEach(id -> devices.findById(id).ifPresent(devices::delete));
        userIds.forEach(id -> users.findById(id).ifPresent(users::delete));
        institutions.findById(institution.getId()).ifPresent(institutions::delete);
    }

    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;

    private void jdbcDeleteClassroom(UUID id) {
        jdbc.update("delete from classroom_model where id = ?", id);
    }

    private InstitutionModel newInstitution(String name) {
        InstitutionModel i = new InstitutionModel();
        i.setName(name);
        i.setAcronym("RB");
        i.setCnpj(String.format("%014d", Math.abs(UUID.randomUUID().getMostSignificantBits()) % 100000000000000L));
        i.setActive(true);
        i.setEmail("rb-" + UUID.randomUUID() + "@test.local");
        return institutions.save(i);
    }

    private Student newStudent(String name) {
        Student s = new Student();
        s.setEmail("rb-" + UUID.randomUUID() + "@test.local");
        s.setName(name);
        s.setPassword("hash-original");
        s.setInstitution(institution);
        s = users.save(s);
        userIds.add(s.getId());
        return s;
    }

    private DeviceModel newDevice() {
        DeviceModel d = devices.save(DeviceModel.builder().name("d").hardwareId("hw-" + UUID.randomUUID()).publicKey("k")
                .status(DeviceStatus.ACTIVE).institution(institution).build());
        deviceIds.add(d.getId());
        return d;
    }

    /** cria o checkpoint já aprovado por outra pessoa */
    private JaversCheckpoint approvedCheckpoint(Object... entities) {
        JaversCheckpoint cp = checkpoints.create(entities[0].getClass().getName(), List.of(entities), "admin-1");
        checkpoints.approve(cp.getId(), "admin-2");
        return cp;
    }

    @Test
    @DisplayName("User: restaura nome e ativo, força passwordChangedAt e tokensRevokedAt, preserva a senha e lista os dispositivos afetados")
    void userRollback() {
        Student student = newStudent("Nome Original");
        DeviceModel device = newDevice();
        JaversCheckpoint cp = approvedCheckpoint(student);

        student.setName("Nome Alterado");
        student.setActive(false);
        student.setPassword("hash-novo");
        users.save(student);
        Instant before = Instant.now().minusSeconds(1);

        RollbackResult result = rollback.rollbackEntity(cp.getId(), User.class, student.getId());

        User restored = users.findById(student.getId()).orElseThrow();
        assertEquals("Nome Original", restored.getName());
        assertTrue(restored.isActive());
        assertEquals("hash-novo", restored.getPassword(), "a senha nunca é restaurada");
        assertNotNull(restored.getPasswordChangedAt());
        assertNotNull(restored.getTokensRevokedAt());
        assertTrue(restored.getPasswordChangedAt().isAfter(before));
        assertTrue(restored.getTokensRevokedAt().isAfter(before));

        assertEquals(List.of(device.getId()), result.candidateDevices());
        assertTrue(result.manualActionRequired().contains("/api/v1/admin/devices/{id}/revoke-tokens"));
        assertTrue(result.manualActionRequired().contains("manualmente"));
        assertTrue(events.received.stream().anyMatch(e -> e instanceof UserRestoredEvent r && r.userId().equals(student.getId())));
        verify(notifier).sendAccountRestored(restored.getEmail(), "Nome Original");
    }

    @Test
    @DisplayName("Checkpoint não aprovado não permite rollback")
    void requiresApproval() {
        Student student = newStudent("Nome");
        JaversCheckpoint cp = checkpoints.create(Student.class.getName(), List.<Object>of(student), "admin-1");

        assertThrows(IllegalStateException.class, () -> rollback.rollbackEntity(cp.getId(), User.class, student.getId()));
    }

    @Test
    @DisplayName("Sem histórico para a entidade: RollbackNotAvailableException")
    void noHistory() {
        Student student = newStudent("Nome");
        JaversCheckpoint cp = approvedCheckpoint(student);

        assertThrows(RollbackNotAvailableException.class, () -> rollback.rollbackEntity(cp.getId(), User.class, UUID.randomUUID()));
    }

    @Test
    @DisplayName("Device: restaura o status e publica DeviceStatusChanged com o status restaurado")
    void deviceRollbackPublishesEvent() {
        DeviceModel device = newDevice();
        JaversCheckpoint cp = approvedCheckpoint(device);
        device.setStatus(DeviceStatus.INACTIVE);
        devices.save(device);

        RollbackResult result = rollback.rollbackEntity(cp.getId(), DeviceModel.class, device.getId());

        assertEquals(DeviceStatus.ACTIVE, devices.findById(device.getId()).orElseThrow().getStatus());
        assertTrue(events.received.stream().anyMatch(e -> e instanceof DeviceStatusChanged d
                && d.deviceId().equals(device.getId()) && d.status() == DeviceStatus.ACTIVE));
        assertTrue(result.candidateDevices().isEmpty(), "só User preenche candidateDevices");
        assertEquals(null, result.manualActionRequired());
    }

    @Test
    @DisplayName("Institution inativa não é reativada automaticamente: continua inativa e pendente de verificação")
    void institutionIsNotReactivated() {
        JaversCheckpoint cp = approvedCheckpoint(institution);
        institution.setActive(false);
        institution.setVerificationStatus(InstitutionVerificationStatus.VERIFIED);
        institutions.save(institution);

        rollback.rollbackEntity(cp.getId(), InstitutionModel.class, institution.getId());

        InstitutionModel restored = institutions.findById(institution.getId()).orElseThrow();
        assertFalse(restored.isActive());
        assertEquals(InstitutionVerificationStatus.PENDING_VERIFICATION, restored.getVerificationStatus());
    }

    @Test
    @DisplayName("Scenario: sem o arquivo de áudio em disco lança RollbackNotAvailableException; com o arquivo restaura o nome")
    void scenarioRequiresAudioFile() throws Exception {
        String audio = UUID.randomUUID() + "_audio.mp3";
        Path file = files.load(audio);
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[] { 1 });
        ScenarioModel scenario = scenarios.save(ScenarioModel.builder().name("cenario").description("d").audioUrl(audio).build());
        JaversCheckpoint cp = approvedCheckpoint(scenario);
        try {
            scenario.setName("outro nome");
            scenarios.save(scenario);

            Files.delete(file);
            assertThrows(RollbackNotAvailableException.class,
                    () -> rollback.rollbackEntity(cp.getId(), ScenarioModel.class, scenario.getId()));
            assertEquals("outro nome", scenarios.findById(scenario.getId()).orElseThrow().getName(), "nada foi alterado");

            Files.write(file, new byte[] { 1 });
            rollback.rollbackEntity(cp.getId(), ScenarioModel.class, scenario.getId());
            assertEquals("cenario", scenarios.findById(scenario.getId()).orElseThrow().getName());
        } finally {
            Files.deleteIfExists(file);
            scenarios.deleteById(scenario.getId());
        }
    }

    @Test
    @DisplayName("Classroom excluída: o rollback restaura a turma e recria os membros removidos na mesma transação")
    void classroomRollbackRestoresMembers() {
        Student student = newStudent("Aluno");
        ClassroomModel classroom = classrooms.save(ClassroomModel.builder().title("Turma").period("2026/2").institution(institution).build());
        classroomIds.add(classroom.getId());
        ClassroomMember member = members.save(ClassroomMember.builder().classroom(classroom).user(student).build());
        JaversCheckpoint cp = approvedCheckpoint(classroom);

        classroom.setDeleted(true);
        classrooms.save(classroom);
        members.delete(member);
        assertTrue(members.findByClassroomId(classroom.getId()).isEmpty());

        rollback.rollbackEntity(cp.getId(), ClassroomModel.class, classroom.getId());

        ClassroomModel restored = classrooms.findById(classroom.getId()).orElseThrow();
        assertFalse(restored.isDeleted());
        List<ClassroomMember> back = members.findByClassroomId(classroom.getId());
        assertEquals(1, back.size());
        memberIds.add(back.get(0).getId());
        assertEquals(1, jdbc.queryForObject("select count(*) from classroom_members where classroom_id = ? and user_id = ?",
                Integer.class, classroom.getId(), student.getId()));
        assertNotEquals(member.getId(), back.get(0).getId(), "o membro é recriado com novo id");
    }

    @Test
    @DisplayName("Rollback em lote restaura todas as entidades do checkpoint e o marca como executado")
    void checkpointBatchRollback() {
        Student a = newStudent("A original");
        Student b = newStudent("B original");
        JaversCheckpoint cp = approvedCheckpoint(a, b);
        a.setName("A mudou");
        b.setName("B mudou");
        users.save(a);
        users.save(b);

        List<RollbackResult> results = rollback.rollbackCheckpoint(cp.getId());

        assertEquals(2, results.size());
        assertEquals("A original", users.findById(a.getId()).orElseThrow().getName());
        assertEquals("B original", users.findById(b.getId()).orElseThrow().getName());
        assertEquals(com.n0hana.echoes_server.infra.javers.checkpoint.JaversCheckpointStatus.EXECUTED,
                checkpointRepository.findById(cp.getId()).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("ClassroomMember individual: recria o membro removido a partir do snapshot")
    void singleMemberRollback() {
        Student student = newStudent("Aluno");
        ClassroomModel classroom = classrooms.save(ClassroomModel.builder().title("T").period("2026/2").institution(institution).build());
        classroomIds.add(classroom.getId());
        ClassroomMember member = members.save(ClassroomMember.builder().classroom(classroom).user(student).build());
        JaversCheckpoint cp = approvedCheckpoint(member);
        members.delete(member);
        assertTrue(members.findByClassroomId(classroom.getId()).isEmpty());

        RollbackResult result = rollback.rollbackEntity(cp.getId(), ClassroomMember.class, member.getId());

        assertEquals(ClassroomMember.class.getName(), result.entityType());
        assertEquals(1, members.findByClassroomId(classroom.getId()).size());
        memberIds.add(members.findByClassroomId(classroom.getId()).get(0).getId());
    }

    @Test
    @DisplayName("User restaurado com e-mail anonimizado publica o evento mas não envia e-mail")
    void anonymizedUserIsNotEmailed() {
        Student student = newStudent("Aluno");
        student.setEmail("deleted_user_x@deleted.local");
        users.save(student);
        JaversCheckpoint cp = approvedCheckpoint(student);
        student.setActive(false);
        users.save(student);

        rollback.rollbackEntity(cp.getId(), User.class, student.getId());

        assertTrue(events.received.stream().anyMatch(e -> e instanceof UserRestoredEvent));
        org.mockito.Mockito.verify(notifier, org.mockito.Mockito.never()).sendAccountRestored(anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("resolveType aceita nome simples ou completo e recusa tipo sem política")
    void resolveType() {
        new com.n0hana.echoes_server.infra.javers.retention.JaversRetentionPolicyInitializer(policies).seed();

        assertEquals(User.class, rollback.resolveType("User"));
        assertEquals(User.class, rollback.resolveType(User.class.getName()));
        assertThrows(IllegalStateException.class, () -> rollback.resolveType("UserTermAcceptance"));
        assertThrows(IllegalStateException.class, () -> rollback.resolveType(" "));
    }
}
