package com.n0hana.echoes_server.user.export;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.n0hana.echoes_server.classroom.ClassroomMember;
import com.n0hana.echoes_server.classroom.ClassroomMemberRepository;
import com.n0hana.echoes_server.classroom.ClassroomModel;
import com.n0hana.echoes_server.classroom.ClassroomRepository;
import com.n0hana.echoes_server.classroom.material.ClassroomMaterial;
import com.n0hana.echoes_server.classroom.material.ClassroomMaterialRepository;
import com.n0hana.echoes_server.infra.logs.AuditLog;
import com.n0hana.echoes_server.infra.logs.AuditLogRepository;
import com.n0hana.echoes_server.infra.security.JwtTokenService;
import com.n0hana.echoes_server.institution.InstitutionModel;
import com.n0hana.echoes_server.institution.InstitutionRepository;
import com.n0hana.echoes_server.term.TermRepository;
import com.n0hana.echoes_server.term.UserTermAcceptanceRepository;
import com.n0hana.echoes_server.term.model.DocumentType;
import com.n0hana.echoes_server.term.model.TermModel;
import com.n0hana.echoes_server.term.model.TermStatus;
import com.n0hana.echoes_server.term.model.UserTermAcceptance;
import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.model.Student;
import com.n0hana.echoes_server.user.model.User;

/**
 * L1: a exportação do titular traz somente os dados dele. Dois usuários com turmas,
 * materiais, aceites e logs; o export de A não pode conter nada de B (HTTP + JWT reais).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnabledIf("com.n0hana.echoes_server.support.RedisAvailability#redisReachable")
class UserDataExportIsolationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenService jwt;
    @Autowired private UserRepository users;
    @Autowired private InstitutionRepository institutions;
    @Autowired private ClassroomRepository classrooms;
    @Autowired private ClassroomMemberRepository members;
    @Autowired private ClassroomMaterialRepository materials;
    @Autowired private TermRepository terms;
    @Autowired private UserTermAcceptanceRepository acceptances;
    @Autowired private AuditLogRepository auditLogs;
    @Autowired private com.n0hana.echoes_server.auth.device.KnownDeviceService knownDevices;

    private static final String UA_A = "Mozilla/5.0 Chrome/120 (titular A)";
    private static final String UA_B1 = "Mozilla/5.0 Firefox/130 (titular B)";
    private static final String UA_B2 = "Mozilla/5.0 Safari/17 (titular B)";

    private InstitutionModel institution;
    private User userA;
    private User userB;
    private ClassroomModel classA;
    private ClassroomModel classB;

    @BeforeEach
    void seed() {
        institution = new InstitutionModel();
        institution.setName("Inst Export");
        institution.setAcronym("EXP");
        institution.setCnpj("11111111000191");
        institution.setActive(true);
        institution.setEmail("inst-" + UUID.randomUUID() + "@test.local");
        institution = institutions.save(institution);

        userA = newUser("A");
        userB = newUser("B");
        classA = newClassroom("Turma-A-exclusiva");
        classB = newClassroom("Turma-B-exclusiva");

        members.save(ClassroomMember.builder().classroom(classA).user(userA).build());
        members.save(ClassroomMember.builder().classroom(classB).user(userB).build());
        newMaterial(classA, userA, "Material-A-exclusivo");
        newMaterial(classB, userB, "Material-B-exclusivo");

        acceptances.save(new UserTermAcceptance(userA, term(DocumentType.TERMS_OF_USE)));
        acceptances.save(new UserTermAcceptance(userB, term(DocumentType.PRIVACY_POLICY)));
        acceptances.save(new UserTermAcceptance(userB, term(DocumentType.COOKIES_POLICY)));

        auditLogs.save(log(userA, "SEED_ACAO_A", "10.0.0.1"));
        auditLogs.save(log(userB, "SEED_ACAO_B", "10.0.0.2"));
        auditLogs.save(log(userB, "SEED_ACAO_B2", "10.0.0.2"));

        knownDevices.registerAndCheckNew(userA.getId(), UA_A);
        knownDevices.registerAndCheckNew(userB.getId(), UA_B1);
        knownDevices.registerAndCheckNew(userB.getId(), UA_B2);
    }

    @AfterEach
    void cleanup() {
        knownDevices.forget(userA.getId());
        knownDevices.forget(userB.getId());
        materials.deleteAll(materials.findByAuthorId(userA.getId()));
        materials.deleteAll(materials.findByAuthorId(userB.getId()));
        members.deleteAll(members.findByUserId(userA.getId()));
        members.deleteAll(members.findByUserId(userB.getId()));
        acceptances.deleteAll(acceptances.findAll().stream()
                .filter(a -> a.getUser().getId().equals(userA.getId()) || a.getUser().getId().equals(userB.getId()))
                .toList());
        classrooms.deleteAll(List.of(classA, classB));
        users.deleteAll(List.of(userA, userB));
        institutions.delete(institution);
    }

    @Test
    @DisplayName("Export autenticado como A retorna só dados de A, nenhum de B")
    void exportOfAContainsOnlyA() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/users/me/export").servletPath("/api/v1/users/me/export")
                .header("Authorization", "Bearer " + jwt.generate(userA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.email").value(userA.getEmail()))
                .andExpect(jsonPath("$.classrooms.length()").value(1))
                .andExpect(jsonPath("$.classrooms[0].title").value("Turma-A-exclusiva"))
                .andExpect(jsonPath("$.materials.length()").value(1))
                .andExpect(jsonPath("$.materials[0].title").value("Material-A-exclusivo"))
                .andExpect(jsonPath("$.termAcceptances.length()").value(1))
                .andExpect(jsonPath("$.termAcceptances[0].type").value("TERMS_OF_USE"))
                .andExpect(jsonPath("$.activityLog[?(@.action=='SEED_ACAO_A')]").exists())
                .andExpect(jsonPath("$.knownDevices.length()").value(1))
                .andExpect(jsonPath("$.knownDevices[0].userAgentSha256").value(sha256(UA_A)))
                .andExpect(jsonPath("$.knownDevices[0].firstSeenAt").exists())
                .andExpect(jsonPath("$.knownDevices[0].expiresAt").exists())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        for (String leak : List.of(userB.getEmail(), userB.getId().toString(), "Turma-B-exclusiva",
                "Material-B-exclusivo", "SEED_ACAO_B", "10.0.0.2", "PRIVACY_POLICY", "COOKIES_POLICY",
                sha256(UA_B1), sha256(UA_B2), "Chrome/120")) {
            assertFalse(body.contains(leak), "export de A vazou dado de B: " + leak);
        }
    }

    @Test
    @DisplayName("Export autenticado como B retorna só dados de B, nenhum de A")
    void exportOfBContainsOnlyB() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/users/me/export").servletPath("/api/v1/users/me/export")
                .header("Authorization", "Bearer " + jwt.generate(userB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classrooms.length()").value(1))
                .andExpect(jsonPath("$.materials.length()").value(1))
                .andExpect(jsonPath("$.termAcceptances.length()").value(2))
                .andExpect(jsonPath("$.knownDevices.length()").value(2))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        for (String leak : List.of(userA.getEmail(), userA.getId().toString(), "Turma-A-exclusiva",
                "Material-A-exclusivo", "SEED_ACAO_A", "10.0.0.1", "TERMS_OF_USE", sha256(UA_A))) {
            assertFalse(body.contains(leak), "export de B vazou dado de A: " + leak);
        }
    }

    private static String sha256(String value) throws Exception {
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    private User newUser(String tag) {
        Student s = new Student();
        s.setEmail("export-" + tag + "-" + UUID.randomUUID() + "@test.local");
        s.setName("Titular " + tag);
        s.setPassword("hash");
        return users.save(s);
    }

    private ClassroomModel newClassroom(String title) {
        return classrooms.save(ClassroomModel.builder()
                .title(title).period("2026/2").institution(institution).build());
    }

    private void newMaterial(ClassroomModel classroom, User author, String title) {
        ClassroomMaterial m = new ClassroomMaterial();
        m.setClassroom(classroom);
        m.setAuthor(author);
        m.setTitle(title);
        m.setOriginalFileName(title + ".pdf");
        m.setStoredFileName(UUID.randomUUID().toString());
        m.setContentType("application/pdf");
        m.setFileSize(10L);
        materials.save(m);
    }

    private TermModel term(DocumentType type) {
        return terms.findFirstByTypeAndStatusOrderByTimestampDesc(type, TermStatus.PUBLISHED)
                .orElseGet(() -> {
                    TermModel t = new TermModel();
                    t.setType(type);
                    t.setVersion("1.0.0");
                    t.setStatus(TermStatus.PUBLISHED);
                    t.setContent("# " + type);
                    return terms.save(t);
                });
    }

    private AuditLog log(User user, String action, String ip) {
        AuditLog l = new AuditLog();
        l.setUserId(user.getId().toString());
        l.setAction(action);
        l.setEntity("User");
        l.setStatus("SUCCESS");
        l.setIp(ip);
        l.setTimestamp(java.time.Instant.now());
        l.setSignature("sig");
        return l;
    }
}
