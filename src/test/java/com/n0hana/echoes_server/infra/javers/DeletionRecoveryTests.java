package com.n0hana.echoes_server.infra.javers;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.n0hana.echoes_server.classroom.ClassroomMember;
import com.n0hana.echoes_server.classroom.ClassroomMemberRepository;
import com.n0hana.echoes_server.classroom.ClassroomModel;
import com.n0hana.echoes_server.classroom.ClassroomRepository;
import com.n0hana.echoes_server.classroom.ClassroomService;
import com.n0hana.echoes_server.infra.logs.AuditLog;
import com.n0hana.echoes_server.infra.logs.AuditLogRepository;
import com.n0hana.echoes_server.infra.logs.AuditSigner;
import com.n0hana.echoes_server.institution.InstitutionModel;
import com.n0hana.echoes_server.institution.InstitutionRepository;
import com.n0hana.echoes_server.institution.InstitutionService;
import com.n0hana.echoes_server.institution.InstitutionVerificationStatus;
import com.n0hana.echoes_server.term.TermService;
import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.model.Admin;
import com.n0hana.echoes_server.user.model.Manager;
import com.n0hana.echoes_server.user.model.Student;
import com.n0hana.echoes_server.user.model.Teacher;
import com.n0hana.echoes_server.user.model.User;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:deletion-recovery;DB_CLOSE_DELAY=-1;MODE=MySQL")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DeletionRecoveryTests {

    private static final String BASE = "/api/v1/admin/restorations";

    @Autowired private MockMvc mvc;
    @Autowired private InstitutionRepository institutions;
    @Autowired private InstitutionService institutionService;
    @Autowired private ClassroomRepository classrooms;
    @Autowired private ClassroomService classroomService;
    @Autowired private ClassroomMemberRepository members;
    @Autowired private UserRepository users;
    @Autowired private AuditLogRepository auditLogs;
    @Autowired private AuditSigner auditSigner;
    @MockitoBean private TermService terms;

    private final Admin requester = admin();
    private final Admin reviewer = admin();

    @Test
    void restoresOnlyTheRosterFromTheDeletionAndPreservesNewEnrollment() throws Exception {
        var institution = institution();
        var manager = manager(institution);
        var classroom = classroom(institution);
        var otherClassroom = classroom(institution);
        var teacher = teacher(institution);
        var student = student(institution);
        var moved = student(institution);
        var former = teacher(institution);
        var removedBeforeDeletion = add(classroom, former);
        members.delete(removedBeforeDeletion);
        add(classroom, teacher);
        add(classroom, student);
        var movedMembership = add(classroom, moved);

        classroomService.delete(manager.getEmail(), classroom.getId());
        add(otherClassroom, moved);
        assertTrue(classrooms.findById(classroom.getId()).isEmpty());
        assertTrue(members.findByClassroomId(classroom.getId()).isEmpty());

        String url = BASE + "/CLASSROOM/" + classroom.getId();
        mvc.perform(get(BASE).param("type", "CLASSROOM").param("status", "AVAILABLE").with(as(requester)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(classroom.getId().toString()));
        mvc.perform(get(url).with(as(requester))).andExpect(status().isOk())
                .andExpect(jsonPath("$.target.rosterRecorded").value(true))
                .andExpect(jsonPath("$.recoverableMembers").value(2))
                .andExpect(jsonPath("$.skippedMembers[0].membershipId").value(movedMembership.getId().toString()));
        mvc.perform(post(url + "/execute").with(as(requester)).with(csrf())).andExpect(status().isConflict());
        requestAndApprove(url);
        mvc.perform(post(url + "/execute").with(as(reviewer)).with(csrf())).andExpect(status().isOk())
                .andExpect(jsonPath("$.recoveredMembers").value(2))
                .andExpect(jsonPath("$.skippedMembers[0].membershipId").value(movedMembership.getId().toString()));
        mvc.perform(post(url + "/execute").with(as(reviewer)).with(csrf())).andExpect(status().isConflict());
        mvc.perform(get(url).with(as(reviewer))).andExpect(status().isOk())
                .andExpect(jsonPath("$.target.status").value("EXECUTED"));

        assertTrue(classrooms.findById(classroom.getId()).orElseThrow().isArchived());
        assertEquals(2, members.findByClassroomId(classroom.getId()).size());
        assertTrue(members.existsByClassroomIdAndUserId(classroom.getId(), teacher.getId()));
        assertTrue(members.existsByClassroomIdAndUserId(classroom.getId(), student.getId()));
        assertFalse(members.existsByClassroomIdAndUserId(classroom.getId(), former.getId()));
        assertTrue(members.existsByClassroomIdAndUserId(otherClassroom.getId(), moved.getId()));
    }

    @Test
    void restoresAnInstitutionWithExistingMembersAndRespectsManagerAccess() throws Exception {
        var institution = institution();
        var manager = manager(institution);
        var teacher = teacher(institution);
        var student = student(institution);
        institutionService.delete(institution.getId());
        String url = BASE + "/INSTITUTION/" + institution.getId();

        mvc.perform(get(BASE).with(as(manager))).andExpect(status().isForbidden());
        mvc.perform(get(url).with(as(manager))).andExpect(status().isForbidden());
        mvc.perform(post(url + "/request").with(as(manager)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(get(url).with(as(requester))).andExpect(status().isOk())
                .andExpect(jsonPath("$.recoverableMembers").value(3));
        requestAndApprove(url);
        mvc.perform(post(url + "/execute").with(as(reviewer)).with(csrf())).andExpect(status().isOk())
                .andExpect(jsonPath("$.recoveredMembers").value(3));

        var restored = institutions.findById(institution.getId()).orElseThrow();
        assertTrue(restored.isActive());
        assertEquals(InstitutionVerificationStatus.VERIFIED, restored.getVerificationStatus());
        assertEquals("ACTIVE", restored.getDeleteToken());
        assertEquals(3, users.findUsersByInstitutionId(restored.getId(), org.springframework.data.domain.Pageable.unpaged())
                .getTotalElements());
        for (var user : List.of(manager, teacher, student))
            assertTrue(users.findById(user.getId()).isPresent());
    }

    @Test
    void displaysInstitutionNamesBeforeAndAfterRestoration() throws Exception {
        var institution = institution();
        var manager = manager(institution);
        UUID id = institution.getId();
        String originalName = institution.getName();
        String url = BASE + "/INSTITUTION/" + id;
        String auditUrl = "/api/v1/audit-logs";

        institutionService.toggleStatus(id);
        institutionService.delete(id);
        assertTrue(institutions.findById(id).isEmpty());
        awaitInstitutionAuditName(id, "TOGGLE_STATUS", originalName);
        awaitInstitutionAuditName(id, "DELETE", originalName);

        saveHistoricalLog(AuditLog.builder().action("UPDATE").entity("Institution")
                .entityId(id.toString()).institutionId(id.toString()).status("FAILED").build());
        saveHistoricalLog(AuditLog.builder().action("DELETION_RESTORE_REQUESTED").entity("DeletionRecovery")
                .entityId(id.toString()).status("SUCCESS").build());
        mvc.perform(get(auditUrl).param("action", "UPDATE").param("institutionId", id.toString())
                        .with(as(requester)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].institutionName").value(originalName));
        mvc.perform(get(auditUrl).param("action", "DELETION_RESTORE_REQUESTED").with(as(requester)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.entityId == '" + id + "')].institutionName",
                        hasItem(originalName)));

        requestAndApprove(url);
        mvc.perform(post(url + "/execute").with(as(reviewer)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetName").doesNotExist());
        for (String action : List.of("DELETION_RESTORE_REQUESTED", "DELETION_RESTORE_APPROVED", "DELETION_RESTORED")) {
            awaitInstitutionAuditName(id, action, originalName);
        }
        mvc.perform(get(auditUrl).param("entity", "DeletionRecovery").with(as(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());

        institution.setName("Instituição renomeada");
        institutionService.update(id, institution);
        awaitInstitutionAuditName(id, "UPDATE", institution.getName());
        awaitInstitutionAuditName(id, "DELETE", originalName);
        awaitInstitutionAuditName(id, "DELETION_RESTORED", originalName);
    }

    private void awaitInstitutionAuditName(UUID id, String action, String name) {
        await().atMost(Duration.ofSeconds(8)).untilAsserted(() ->
                mvc.perform(get("/api/v1/audit-logs").param("institutionId", id.toString())
                                .param("action", action).param("status", "SUCCESS").with(as(requester)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.content[0].entityName").value(name))
                        .andExpect(jsonPath("$.content[0].institutionName").value(name)));
    }

    private void saveHistoricalLog(AuditLog log) {
        log.setTimestamp(Instant.parse("2026-01-01T00:00:00Z"));
        log.setSignature(auditSigner.sign(log));
        auditLogs.save(log);
    }

    @Test
    void leavesAnInstitutionDeletedWhenItsIdentityIsReusedAfterApproval() throws Exception {
        var institution = institution();
        institutionService.delete(institution.getId());
        String url = BASE + "/INSTITUTION/" + institution.getId();
        requestAndApprove(url);
        var replacement = institutions.save(InstitutionModel.builder().name("Nova instituição").acronym("NOVA")
                .cnpj(institution.getCnpj()).email(UUID.randomUUID() + "@test.local").build());
        mvc.perform(post(url + "/execute").with(as(reviewer)).with(csrf())).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Outra instituição já usa o mesmo CNPJ ou e-mail."));
        assertTrue(institutions.findById(institution.getId()).isEmpty());
        assertTrue(institutions.findById(replacement.getId()).isPresent());
        mvc.perform(get(url).with(as(reviewer))).andExpect(jsonPath("$.target.status").value("APPROVED"));
    }

    @Test
    void executesARecoveryOnlyOnceWithConcurrentRequests() throws Exception {
        var institution = institution();
        institutionService.delete(institution.getId());
        String url = BASE + "/INSTITUTION/" + institution.getId();
        requestAndApprove(url);

        var start = new CountDownLatch(1);
        Callable<Integer> execute = () -> {
            start.await(5, TimeUnit.SECONDS);
            return mvc.perform(post(url + "/execute").with(as(reviewer)).with(csrf())).andReturn().getResponse().getStatus();
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(execute);
            var second = executor.submit(execute);
            start.countDown();
            var statuses = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertEquals(List.of(200, 409), statuses.stream().sorted().toList());
        }
    }

    @Test
    void restoresLegacyClassroomWithoutInventingItsRoster() throws Exception {
        var institution = institution();
        var classroom = classroom(institution);
        classroom.setDeleted(true);
        classrooms.save(classroom);
        institutionService.delete(institution.getId());
        String url = BASE + "/CLASSROOM/" + classroom.getId();
        mvc.perform(get(url).with(as(requester))).andExpect(status().isOk())
                .andExpect(jsonPath("$.target.rosterRecorded").value(false))
                .andExpect(jsonPath("$.blockedReason").exists());
        mvc.perform(post(url + "/request").with(as(requester)).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(post(url + "/approve").with(as(reviewer)).with(csrf())).andExpect(status().isConflict());
        String parent = BASE + "/INSTITUTION/" + institution.getId();
        requestAndApprove(parent);
        mvc.perform(post(parent + "/execute").with(as(reviewer)).with(csrf())).andExpect(status().isOk());
        mvc.perform(post(url + "/approve").with(as(reviewer)).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(post(url + "/execute").with(as(reviewer)).with(csrf())).andExpect(status().isOk())
                .andExpect(jsonPath("$.recoveredMembers").value(0));
        assertTrue(classrooms.findById(classroom.getId()).isPresent());
    }

    private void requestAndApprove(String url) throws Exception {
        mvc.perform(post(url + "/request").with(as(requester)).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(post(url + "/approve").with(as(requester)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post(url + "/approve").with(as(reviewer)).with(csrf())).andExpect(status().isNoContent());
    }

    private InstitutionModel institution() {
        String cnpj = String.format("%014d", Math.abs((long) UUID.randomUUID().hashCode()));
        return institutions.save(InstitutionModel.builder().name("Instituição de teste").acronym("TEST")
                .cnpj(cnpj).email(UUID.randomUUID() + "@test.local")
                .verificationStatus(InstitutionVerificationStatus.VERIFIED).build());
    }

    private ClassroomModel classroom(InstitutionModel institution) {
        return classrooms.save(ClassroomModel.builder().title("Turma de teste").period("2026/1")
                .institution(institution).archived(true).build());
    }

    private ClassroomMember add(ClassroomModel classroom, User user) {
        return members.save(ClassroomMember.builder().classroom(classroom).user(user)
                .studentId(user instanceof Student ? user.getId() : null).build());
    }

    private Manager manager(InstitutionModel institution) {
        var user = new Manager();
        user.setInstitution(institution);
        return users.save(initialize(user));
    }

    private Teacher teacher(InstitutionModel institution) {
        var user = new Teacher();
        user.setInstitution(institution);
        return users.save(initialize(user));
    }

    private Student student(InstitutionModel institution) {
        var user = new Student();
        user.setInstitution(institution);
        return users.save(initialize(user));
    }

    private static Admin admin() {
        var admin = initialize(new Admin());
        admin.setId(UUID.randomUUID());
        return admin;
    }

    private static <T extends User> T initialize(T user) {
        user.setName("Usuário de teste");
        user.setEmail(UUID.randomUUID() + "@test.local");
        user.setPassword("unused");
        return user;
    }

    private static RequestPostProcessor as(User user) {
        return authentication(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}
