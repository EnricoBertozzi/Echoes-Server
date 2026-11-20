package com.n0hana.echoes_server.institution;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.n0hana.echoes_server.classroom.ClassroomModel;
import com.n0hana.echoes_server.classroom.ClassroomRepository;
import com.n0hana.echoes_server.device.DeviceModel;
import com.n0hana.echoes_server.device.DeviceRepository;
import com.n0hana.echoes_server.device.DeviceStatus;
import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.model.Manager;
import com.n0hana.echoes_server.user.model.Student;
import com.n0hana.echoes_server.user.model.Teacher;
import com.n0hana.echoes_server.user.model.User;

/**
 * Prova contra o banco que a exclusão de uma instituição anonimiza a linha e
 * desliga turmas, dispositivos e usuários na mesma transação.
 */
@SpringBootTest
@ActiveProfiles("test")
class InstitutionDeletionCascadeIntegrationTest {

    private static final String CNPJ = "98989898000198";
    private static final String HARDWARE_ID = "AA:BB:CC:DD:EE:99";

    @Autowired
    private InstitutionRepository institutionRepository;
    @Autowired
    private InstitutionService institutionService;
    @Autowired
    private ClassroomRepository classroomRepository;
    @Autowired
    private DeviceRepository deviceRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JdbcTemplate jdbc;

    private UUID institutionId;
    private UUID classroomId;
    private UUID[] userIds;

    @AfterEach
    void limpar() {
        jdbc.update("delete from device_tb where hardware_id = ?", HARDWARE_ID);
        if (userIds != null) {
            jdbc.update("delete from users where id in (?, ?, ?)", userIds);
        }
        if (classroomId != null) {
            jdbc.update("delete from classroom_model where id = ?", classroomId);
        }
        if (institutionId != null) {
            jdbc.update("delete from institutions where id = ?", institutionId);
        }
    }

    @Test
    @DisplayName("delete: anonimiza a instituição, exclui turmas, inativa devices e usuários")
    void cascadeDaExclusao() {
        InstitutionModel institution = institutionRepository.saveAndFlush(InstitutionModel.builder()
                .name("Instituição Cascade")
                .acronym("IC")
                .cnpj(CNPJ)
                .email("cascade@test.local")
                .build());

        institutionId = institution.getId();
        classroomId = classroomRepository.saveAndFlush(ClassroomModel.builder()
                .title("Turma do cascade")
                .period("2026/1")
                .institution(institution)
                .build()).getId();

        deviceRepository.saveAndFlush(DeviceModel.builder()
                .name("ESP do cascade")
                .hardwareId(HARDWARE_ID)
                .publicKey("pem")
                .status(DeviceStatus.ACTIVE)
                .institution(institution)
                .build());

        UUID managerId = saveUser(new Manager(), "gestor-cascade@test.local", institution).getId();
        UUID teacherId = saveUser(new Teacher(), "professor-cascade@test.local", institution).getId();
        UUID studentId = saveUser(new Student(), "aluno-cascade@test.local", institution).getId();
        userIds = new UUID[] { managerId, teacherId, studentId };

        institutionService.delete(institution.getId());

        // a linha continua no banco (soft delete), mas sem dados identificáveis
        assertThat(jdbc.queryForObject("select deleted from institutions where id = ?", Boolean.class,
                institution.getId())).isTrue();
        assertThat(jdbc.queryForObject("select active from institutions where id = ?", Boolean.class,
                institution.getId())).isFalse();
        assertThat(jdbc.queryForObject("select name from institutions where id = ?", String.class,
                institution.getId())).startsWith("Instituição excluída");
        assertThat(jdbc.queryForObject("select acronym from institutions where id = ?", String.class,
                institution.getId())).isEqualTo("EXCLUIDA");
        assertThat(jdbc.queryForObject("select cnpj from institutions where id = ?", String.class,
                institution.getId())).isEqualTo("ANONIMIZADO");
        assertThat(jdbc.queryForObject("select email from institutions where id = ?", String.class,
                institution.getId())).endsWith("@deleted.local");
        assertThat(jdbc.queryForObject("select address from institutions where id = ?", String.class,
                institution.getId())).isNull();

        // a turma é excluída logicamente
        assertThat(jdbc.queryForObject("select deleted from classroom_model where id = ?", Boolean.class,
                classroomId)).isTrue();

        // o dispositivo deixa de operar
        assertThat(deviceRepository.findByHardwareId(HARDWARE_ID).orElseThrow().getStatus())
                .isEqualTo(DeviceStatus.INACTIVE);

        // os usuários são desativados, mas os registros permanecem
        for (UUID userId : userIds) {
            assertThat(userRepository.findById(userId)).isPresent();
            assertThat(jdbc.queryForObject("select active from users where id = ?", Boolean.class, userId)).isFalse();
        }
    }

    private <T extends User> T saveUser(T user, String email, InstitutionModel institution) {
        user.setName(email);
        user.setEmail(email);
        user.setPassword("hash");
        user.setActive(true);

        if (user instanceof Manager manager) {
            manager.setInstitution(institution);
        } else if (user instanceof Teacher teacher) {
            teacher.setInstitution(institution);
        } else if (user instanceof Student student) {
            student.setInstitution(institution);
        }

        return userRepository.saveAndFlush(user);
    }
}
