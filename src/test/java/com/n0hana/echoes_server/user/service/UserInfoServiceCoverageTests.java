package com.n0hana.echoes_server.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.n0hana.echoes_server.institution.InstitutionModel;
import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.dto.UserDTO;
import com.n0hana.echoes_server.user.dto.UserEmailDTO;
import com.n0hana.echoes_server.user.exception.InvalidUserTypeException;
import com.n0hana.echoes_server.user.exception.UserNotFoundException;
import com.n0hana.echoes_server.user.model.Admin;
import com.n0hana.echoes_server.user.model.Manager;
import com.n0hana.echoes_server.user.model.Student;
import com.n0hana.echoes_server.user.model.Teacher;
import com.n0hana.echoes_server.user.model.User;
import com.n0hana.echoes_server.user.model.UserRole;

@ExtendWith(MockitoExtension.class)
class UserInfoServiceCoverageTests {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserInfoService service;

    private final Pageable pageable = PageRequest.of(0, 10);

    private static <T extends User> T user(T user, String name, String email) {
        user.setId(UUID.randomUUID());
        user.setName(name);
        user.setEmail(email);
        return user;
    }

    private static InstitutionModel institution() {
        InstitutionModel institution = new InstitutionModel();
        institution.setId(UUID.randomUUID());
        return institution;
    }

    @Test
    @DisplayName("findAdminById/ManagerById/TeacherById/StudentById devolvem o DTO do tipo certo")
    void findByIdPerType() {
        InstitutionModel inst = institution();
        Admin admin = user(new Admin(), "Admin", "admin@example.com");
        Manager manager = user(new Manager(), "Gestor", "gestor@example.com");
        manager.setInstitution(inst);
        Teacher teacher = user(new Teacher(), "Prof", "prof@example.com");
        teacher.setInstitution(inst);
        Student student = user(new Student(), "Aluno", "aluno@example.com");
        student.setInstitution(inst);
        for (User u : List.of(admin, manager, teacher, student)) {
            when(userRepository.findById(u.getId())).thenReturn(Optional.of(u));
        }

        UserDTO a = service.findAdminById(admin.getId());
        UserDTO m = service.findManagerById(manager.getId());
        UserDTO t = service.findTeacherById(teacher.getId());
        UserDTO s = service.findStudentById(student.getId());

        assertEquals(UserRole.ADMIN, a.role());
        assertNull(a.institutionId());
        assertEquals("admin@example.com", a.email());
        assertEquals(UserRole.MANAGER, m.role());
        assertEquals(inst.getId(), m.institutionId());
        assertEquals(UserRole.TEACHER, t.role());
        assertEquals(UserRole.STUDENT, s.role());
        assertEquals(inst.getId(), s.institutionId());
    }

    @Test
    @DisplayName("findById de usuário inativo ou inexistente lança UserNotFoundException")
    void findByIdNotFound() {
        Student inactive = user(new Student(), "Aluno", "aluno@example.com");
        inactive.setActive(false);
        UUID missing = UUID.randomUUID();
        when(userRepository.findById(inactive.getId())).thenReturn(Optional.of(inactive));
        when(userRepository.findById(missing)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> service.findStudentById(inactive.getId()));
        assertThrows(UserNotFoundException.class, () -> service.findAdminById(missing));
    }

    @Test
    @DisplayName("findManagerById/TeacherById com tipo errado lança InvalidUserTypeException")
    void findByIdWrongType() {
        Student student = user(new Student(), "Aluno", "aluno@example.com");
        when(userRepository.findById(student.getId())).thenReturn(Optional.of(student));

        assertThrows(InvalidUserTypeException.class, () -> service.findManagerById(student.getId()));
        assertThrows(InvalidUserTypeException.class, () -> service.findTeacherById(student.getId()));
        assertThrows(InvalidUserTypeException.class, () -> service.findAdminById(student.getId()));
    }

    @Test
    @DisplayName("Listagens (admins, gestores, professores, estudantes) mascaram o e-mail")
    void listingsMaskEmails() {
        UUID instId = UUID.randomUUID();
        Admin admin = user(new Admin(), "Admin", "maria@escola.com");
        Manager manager = user(new Manager(), "Gestor", "joao@escola.com");
        Teacher teacher = user(new Teacher(), "Prof", "ana@escola.com");
        Student student = user(new Student(), "Aluno", "b@escola.com");
        when(userRepository.findAdmins(pageable)).thenReturn(new PageImpl<>(List.of(admin)));
        when(userRepository.findManagers(instId, pageable)).thenReturn(new PageImpl<>(List.of(manager)));
        when(userRepository.findTeachers(instId, pageable)).thenReturn(new PageImpl<>(List.of(teacher)));
        when(userRepository.findStudents(instId, pageable)).thenReturn(new PageImpl<>(List.of(student)));

        assertEquals("m***@escola.com", service.findAdmins(pageable).getContent().get(0).email());
        assertEquals("j***@escola.com", service.findManagers(instId, pageable).getContent().get(0).email());
        assertEquals("a***@escola.com", service.findTeachers(instId, pageable).getContent().get(0).email());
        assertEquals("*@escola.com", service.findStudents(instId, pageable).getContent().get(0).email());
    }

    @Test
    @DisplayName("findAdminByName pesquisa pelo nome e mascara o e-mail")
    void findAdminByName() {
        Admin admin = user(new Admin(), "Maria", "maria@escola.com");
        when(userRepository.findAdminsByName("Mar", pageable)).thenReturn(new PageImpl<>(List.of(admin)));

        Page<UserDTO> result = service.findAdminByName("Mar", pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals("Maria", result.getContent().get(0).name());
        assertEquals("m***@escola.com", result.getContent().get(0).email());
        verify(userRepository).findAdminsByName("Mar", pageable);
    }

    @Test
    @DisplayName("findEmail devolve o e-mail completo com a instituição (gestor/professor/estudante)")
    void findEmailWithInstitution() {
        InstitutionModel inst = institution();
        Teacher teacher = user(new Teacher(), "Prof", "prof@escola.com");
        teacher.setInstitution(inst);
        when(userRepository.findById(teacher.getId())).thenReturn(Optional.of(teacher));

        UserEmailDTO dto = service.findEmail(teacher.getId());

        assertEquals(new UserEmailDTO(teacher.getId(), "Prof", "prof@escola.com", inst.getId()), dto);
    }

    @Test
    @DisplayName("findEmail de administrador devolve institutionId nulo")
    void findEmailAdminWithoutInstitution() {
        Admin admin = user(new Admin(), "Admin", "admin@escola.com");
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));

        UserEmailDTO dto = service.findEmail(admin.getId());

        assertEquals("admin@escola.com", dto.email());
        assertNull(dto.institutionId());
    }

    @Test
    @DisplayName("findEmail de usuário inativo ou inexistente lança UserNotFoundException")
    void findEmailNotFound() {
        Admin inactive = user(new Admin(), "Admin", "admin@escola.com");
        inactive.setActive(false);
        UUID missing = UUID.randomUUID();
        when(userRepository.findById(inactive.getId())).thenReturn(Optional.of(inactive));
        when(userRepository.findById(missing)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> service.findEmail(inactive.getId()));
        assertThrows(UserNotFoundException.class, () -> service.findEmail(missing));
    }
}
