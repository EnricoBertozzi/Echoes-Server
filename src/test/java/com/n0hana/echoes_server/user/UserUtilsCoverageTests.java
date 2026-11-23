package com.n0hana.echoes_server.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.n0hana.echoes_server.institution.InstitutionModel;
import com.n0hana.echoes_server.user.dto.UserDTO;
import com.n0hana.echoes_server.user.model.Admin;
import com.n0hana.echoes_server.user.model.Manager;
import com.n0hana.echoes_server.user.model.Student;
import com.n0hana.echoes_server.user.model.Teacher;
import com.n0hana.echoes_server.user.model.User;
import com.n0hana.echoes_server.user.model.UserRole;

class UserUtilsCoverageTests {

    private static InstitutionModel institution() {
        InstitutionModel institution = new InstitutionModel();
        institution.setId(UUID.randomUUID());
        return institution;
    }

    private static <T extends User> T user(T user) {
        user.setId(UUID.randomUUID());
        user.setName("Maria");
        user.setEmail("maria@escola.com");
        user.setMustChangePassword(true);
        return user;
    }

    @Test
    @DisplayName("normalizeEmail remove espaços e converte para minúsculas")
    void normalizeEmail() {
        assertEquals("a@b.com", UserUtils.normalizeEmail("  A@B.Com \n"));
    }

    @Test
    @DisplayName("institutionOf devolve a instituição de gestor, professor e estudante")
    void institutionOfSubclasses() {
        InstitutionModel inst = institution();
        Manager manager = user(new Manager());
        manager.setInstitution(inst);
        Teacher teacher = user(new Teacher());
        teacher.setInstitution(inst);
        Student student = user(new Student());
        student.setInstitution(inst);

        assertSame(inst, UserUtils.institutionOf(manager));
        assertSame(inst, UserUtils.institutionOf(teacher));
        assertSame(inst, UserUtils.institutionOf(student));
    }

    @Test
    @DisplayName("institutionOf de administrador é nulo")
    void institutionOfAdmin() {
        assertNull(UserUtils.institutionOf(user(new Admin())));
    }

    @Test
    @DisplayName("toDTO inclui o e-mail completo, instituição e a flag mustChangePassword")
    void toDtoFull() {
        InstitutionModel inst = institution();
        Student student = user(new Student());
        student.setInstitution(inst);

        UserDTO dto = UserUtils.toDTO(student);

        assertEquals(student.getId(), dto.id());
        assertEquals("Maria", dto.name());
        assertEquals("maria@escola.com", dto.email());
        assertEquals(inst.getId(), dto.institutionId());
        assertEquals(UserRole.STUDENT, dto.role());
        assertTrue(dto.mustChangePassword());
    }

    @Test
    @DisplayName("toDTO de administrador tem institutionId nulo")
    void toDtoAdmin() {
        UserDTO dto = UserUtils.toDTO(user(new Admin()));

        assertNull(dto.institutionId());
        assertEquals(UserRole.ADMIN, dto.role());
    }

    @Test
    @DisplayName("toDTO de gestor sem instituição tem institutionId nulo")
    void toDtoManagerWithoutInstitution() {
        assertNull(UserUtils.toDTO(user(new Manager())).institutionId());
    }

    @Test
    @DisplayName("toMaskedDTO mascara o e-mail e zera mustChangePassword")
    void toMaskedDto() {
        InstitutionModel inst = institution();
        Teacher teacher = user(new Teacher());
        teacher.setInstitution(inst);

        UserDTO dto = UserUtils.toMaskedDTO(teacher);

        assertEquals("m***@escola.com", dto.email());
        assertEquals(inst.getId(), dto.institutionId());
        assertEquals(UserRole.TEACHER, dto.role());
        assertFalse(dto.mustChangePassword());
        assertEquals("Maria", dto.name());
    }

    @Test
    @DisplayName("maskEmail cobre nulo, sem arroba, parte local curta e comum")
    void maskEmail() {
        assertNull(UserUtils.maskEmail(null));
        assertEquals("semarroba", UserUtils.maskEmail("semarroba"));
        assertEquals("*@x.com", UserUtils.maskEmail("a@x.com"));
        assertEquals("*@x.com", UserUtils.maskEmail("@x.com"));
        assertEquals("j***@x.com", UserUtils.maskEmail("joao@x.com"));
        assertEquals("a***@x.com", UserUtils.maskEmail("ab@x.com"));
    }
}
