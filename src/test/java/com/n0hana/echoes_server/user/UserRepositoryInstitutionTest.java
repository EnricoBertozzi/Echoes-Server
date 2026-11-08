package com.n0hana.echoes_server.user;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import com.n0hana.echoes_server.institution.InstitutionModel;
import com.n0hana.echoes_server.institution.InstitutionRepository;
import com.n0hana.echoes_server.user.model.Admin;
import com.n0hana.echoes_server.user.model.Manager;
import com.n0hana.echoes_server.user.model.Student;
import com.n0hana.echoes_server.user.model.Teacher;
import com.n0hana.echoes_server.user.model.User;
import com.n0hana.echoes_server.user.model.UserRole;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class UserRepositoryInstitutionTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private InstitutionRepository institutionRepository;

    private InstitutionModel institutionA;
    private InstitutionModel institutionB;

    @BeforeEach
    void setUp() {
        institutionA = institutionRepository.save(institution("11111111000111", "a@teste.com"));
        institutionB = institutionRepository.save(institution("22222222000122", "b@teste.com"));

        // instituicao a tem manager, teacher, student e um teacher inativo
        Manager manager = new Manager();
        manager.setInstitution(institutionA);
        userRepository.save(fill(manager, "manager@a.com"));

        Teacher teacher = new Teacher();
        teacher.setInstitution(institutionA);
        userRepository.save(fill(teacher, "teacher@a.com"));

        Student student = new Student();
        student.setInstitution(institutionA);
        userRepository.save(fill(student, "student@a.com"));

        Teacher inactive = new Teacher();
        inactive.setInstitution(institutionA);
        inactive.setActive(false);
        userRepository.save(fill(inactive, "inactive@a.com"));

        // instituicao b tem um student e o admin nao tem instituicao
        Student other = new Student();
        other.setInstitution(institutionB);
        userRepository.save(fill(other, "student@b.com"));

        userRepository.save(fill(new Admin(), "admin@teste.com"));
    }

    @Test
    @DisplayName("Traz todos os papéis ativos da instituição e ignora inativos, admin e outras instituições")
    void shouldReturnActiveUsersOfAllRolesFromInstitution() {
        Page<User> result = userRepository.findByInstitution(institutionA.getId(), PageRequest.of(0, 10));

        Set<UserRole> roles = result.getContent().stream()
                .map(User::getUserRole)
                .collect(Collectors.toSet());

        assertEquals(3, result.getTotalElements());
        assertEquals(Set.of(UserRole.MANAGER, UserRole.TEACHER, UserRole.STUDENT), roles);
    }

    @Test
    @DisplayName("Não mistura usuários de instituições diferentes")
    void shouldNotMixInstitutions() {
        Page<User> result = userRepository.findByInstitution(institutionB.getId(), PageRequest.of(0, 10));

        assertEquals(1, result.getTotalElements());
        assertEquals("student@b.com", result.getContent().get(0).getEmail());
    }

    @Test
    @DisplayName("Instituição sem usuários devolve página vazia")
    void shouldReturnEmptyPageForUnknownInstitution() {
        Page<User> result = userRepository.findByInstitution(UUID.randomUUID(), PageRequest.of(0, 10));

        assertEquals(0, result.getTotalElements());
    }

    @Test
    @DisplayName("Paginação respeita o tamanho da página e o total")
    void shouldPaginate() {
        Page<User> result = userRepository.findByInstitution(institutionA.getId(), PageRequest.of(0, 2));

        assertEquals(2, result.getContent().size());
        assertEquals(3, result.getTotalElements());
        assertEquals(2, result.getTotalPages());
    }

    private InstitutionModel institution(String cnpj, String email) {
        return InstitutionModel.builder()
                .name("Instituição " + cnpj)
                .acronym("INS")
                .cnpj(cnpj)
                .email(email)
                .build();
    }

    private <U extends User> U fill(U user, String email) {
        user.setName(email);
        user.setEmail(email);
        user.setPassword("senha");
        return user;
    }
}
