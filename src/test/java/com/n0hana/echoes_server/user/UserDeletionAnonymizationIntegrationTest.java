package com.n0hana.echoes_server.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.n0hana.echoes_server.classroom.ClassroomMemberRepository;
import com.n0hana.echoes_server.classroom.ClassroomMember;
import com.n0hana.echoes_server.classroom.ClassroomModel;
import com.n0hana.echoes_server.classroom.ClassroomRepository;
import com.n0hana.echoes_server.classroom.material.ClassroomMaterial;
import com.n0hana.echoes_server.classroom.material.ClassroomMaterialRepository;
import com.n0hana.echoes_server.infra.security.JwtTokenService;
import com.n0hana.echoes_server.institution.InstitutionModel;
import com.n0hana.echoes_server.institution.InstitutionRepository;
import com.n0hana.echoes_server.term.TermRepository;
import com.n0hana.echoes_server.term.UserTermAcceptanceRepository;
import com.n0hana.echoes_server.term.model.DocumentType;
import com.n0hana.echoes_server.term.model.TermModel;
import com.n0hana.echoes_server.term.model.TermStatus;
import com.n0hana.echoes_server.term.model.UserTermAcceptance;
import com.n0hana.echoes_server.user.model.Teacher;
import com.n0hana.echoes_server.user.model.User;

/**
 * RF06: a exclusão anonimiza o titular sem apagar a linha. Os aceites de termos
 * (prova do consentimento) e os materiais continuam apontando para o mesmo id,
 * agora anonimizado: a integridade referencial se mantém e nada identifica a pessoa.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnabledIf("com.n0hana.echoes_server.support.RedisAvailability#redisReachable")
class UserDeletionAnonymizationIntegrationTest {

    private static final String ORIGINAL_NAME = "Professora Nome Verdadeiro";

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenService jwt;
    @Autowired private UserRepository users;
    @Autowired private InstitutionRepository institutions;
    @Autowired private ClassroomRepository classrooms;
    @Autowired private ClassroomMemberRepository members;
    @Autowired private ClassroomMaterialRepository materials;
    @Autowired private TermRepository terms;
    @Autowired private UserTermAcceptanceRepository acceptances;
    @Autowired private com.n0hana.echoes_server.auth.device.KnownDeviceService knownDevices;

    private InstitutionModel institution;
    private Teacher teacher;
    private String originalEmail;
    private ClassroomModel classroom;
    private int acceptedBefore;

    @BeforeEach
    void seed() {
        institution = new InstitutionModel();
        institution.setName("Inst RF06");
        institution.setAcronym("RF6");
        institution.setCnpj("22222222000191");
        institution.setActive(true);
        institution.setEmail("inst-" + UUID.randomUUID() + "@test.local");
        institution = institutions.save(institution);

        originalEmail = "rf06-" + UUID.randomUUID() + "@test.local";
        Teacher t = new Teacher();
        t.setEmail(originalEmail);
        t.setName(ORIGINAL_NAME);
        t.setPassword("hash");
        t.setInstitution(institution);
        teacher = (Teacher) users.save(t);

        classroom = classrooms.save(ClassroomModel.builder()
                .title("Turma RF06").period("2026/2").institution(institution).build());
        members.save(ClassroomMember.builder().classroom(classroom).user(teacher).build());

        ClassroomMaterial m = new ClassroomMaterial();
        m.setClassroom(classroom);
        m.setAuthor(teacher);
        m.setTitle("Material do professor");
        m.setOriginalFileName("aula.pdf");
        m.setStoredFileName(UUID.randomUUID().toString());
        m.setContentType("application/pdf");
        m.setFileSize(10L);
        materials.save(m);

        // aceita todos os termos publicados (outros testes podem ter publicado mais de um)
        term(DocumentType.TERMS_OF_USE);
        for (TermModel published : terms.findAll()) {
            if (published.getStatus() == TermStatus.PUBLISHED)
                acceptances.save(new UserTermAcceptance(teacher, published));
        }
        acceptedBefore = acceptancesOf(teacher).size();
    }

    private List<UserTermAcceptance> acceptancesOf(User user) {
        return acceptances.findAll().stream().filter(a -> a.getUser().getId().equals(user.getId())).toList();
    }

    @AfterEach
    void cleanup() {
        materials.deleteAll(materials.findByAuthorId(teacher.getId()));
        members.deleteAll(members.findByUserId(teacher.getId()));
        acceptances.deleteAll(acceptances.findAll().stream()
                .filter(a -> a.getUser().getId().equals(teacher.getId())).toList());
        classrooms.delete(classroom);
        knownDevices.forget(teacher.getId());
        users.deleteById(teacher.getId());
        institutions.delete(institution);
    }

    @Test
    @DisplayName("Excluir a conta anonimiza o titular e preserva aceites e materiais ligados ao mesmo id")
    void deletionAnonymizesButKeepsReferences() throws Exception {
        String token = "Bearer " + jwt.generate(teacher);
        UUID otherUser = UUID.randomUUID();
        knownDevices.registerAndCheckNew(teacher.getId(), "Mozilla/5.0 Chrome/120");
        knownDevices.registerAndCheckNew(teacher.getId(), "Mozilla/5.0 Firefox/130");
        knownDevices.registerAndCheckNew(otherUser, "Mozilla/5.0 Safari/17");
        assertEquals(2, knownDevices.list(teacher.getId()).size());

        mockMvc.perform(delete("/api/v1/users/me").header("Authorization", token))
                .andExpect(status().isNoContent());

        // a linha continua existindo (exclusão lógica) e não guarda mais nenhum dado pessoal
        User after = users.findById(teacher.getId()).orElseThrow();
        assertFalse(after.isActive());
        assertNull(after.getName());
        assertNull(after.getPassword());
        assertTrue(after.getEmail().startsWith("deleted_user_"));
        assertFalse(after.getEmail().contains(originalEmail));
        assertNull(((Teacher) after).getInstitution());

        // navegadores conhecidos (Redis) apagados; os de outro usuário continuam
        assertTrue(knownDevices.list(teacher.getId()).isEmpty());
        assertEquals(1, knownDevices.list(otherUser).size());
        knownDevices.forget(otherUser);

        // vínculos com turma foram liberados
        assertTrue(members.findByUserId(teacher.getId()).isEmpty());

        // aceite de termos: prova do consentimento preservada, apontando para o titular anonimizado
        List<UserTermAcceptance> kept = acceptancesOf(teacher);
        assertTrue(acceptedBefore >= 1);
        assertEquals(acceptedBefore, kept.size());
        kept.forEach(a -> {
            assertNotNull(a.getAcceptedAt());
            assertNull(a.getRevokedAt());
        });

        // material: continua existindo, com o mesmo autor (id), que já está anonimizado
        List<ClassroomMaterial> kept2 = materials.findByAuthorId(teacher.getId());
        assertEquals(1, kept2.size());
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
}
