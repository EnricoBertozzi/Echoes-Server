package com.n0hana.echoes_server.infra.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.n0hana.echoes_server.institution.InstitutionModel;
import com.n0hana.echoes_server.institution.InstitutionRepository;
import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.model.Admin;
import com.n0hana.echoes_server.user.model.Manager;
import com.n0hana.echoes_server.user.model.Student;
import com.n0hana.echoes_server.user.model.User;

@ExtendWith(MockitoExtension.class)
class AccessGuardTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private InstitutionRepository institutionRepository;

    @InjectMocks
    private AccessGuard guard;

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Verifica o acesso de usuários admin a qualquer instituição")
    void adminAccessesAnything() {
        // Arrange
        InstitutionModel institution = mock(InstitutionModel.class);
        when(institution.getId()).thenReturn(UUID.randomUUID());
        loginAs(new Admin());

        // Act e Assert
        assertTrue(guard.canAccess(UUID.randomUUID()));
        assertTrue(guard.isInstituicaoUsuario(institution.getId()));
    }

    @Test
    @DisplayName("Verifica se o estudante pode acessar apenas seus dados")
    void studentAccessesOnlyHimself() {
        // Arrange
        InstitutionModel institution = mock(InstitutionModel.class);
        Student student = student(institution);
        loginAs(student);

        // Act e Assert
        assertTrue(guard.canAccess(student.getId()));
        assertFalse(guard.canAccess(UUID.randomUUID()));
    }

    @Test
    @DisplayName("Verifica se o usuário pode acessar apenas sua instituição")
    void managerAccessesOnlyOwnInstitution() {
        // Arrange
        InstitutionModel institution = mock(InstitutionModel.class);
        InstitutionModel other = mock(InstitutionModel.class);

        Manager manager = new Manager();
        manager.setId(UUID.randomUUID());
        manager.setInstitution(institution);

        Student student = student(institution);
        Student foreignStudent = student(other);

        when(institution.getId()).thenReturn(UUID.randomUUID());
        when(other.getId()).thenReturn(UUID.randomUUID());

        when(userRepository.findById(student.getId())).thenReturn(Optional.of(student));
        when(userRepository.findById(foreignStudent.getId())).thenReturn(Optional.of(foreignStudent));
        when(institutionRepository.findById(student.getInstitution().getId())).thenReturn(Optional.of(student.getInstitution()));
        when(institutionRepository.findById(foreignStudent.getInstitution().getId())).thenReturn(Optional.of(foreignStudent.getInstitution()));

        loginAs(manager);

        // Act e Assert
        assertTrue(guard.canAccess(student.getId()));
        assertFalse(guard.canAccess(foreignStudent.getId()));
        assertTrue(guard.isInstituicaoUsuario(institution.getId()));
        assertFalse(guard.isInstituicaoUsuario(other.getId()));
        assertFalse(guard.isInstituicaoUsuario(null));
    }

    @Test
    @DisplayName("Gestor acessa aluno da própria instituição mesmo com instâncias diferentes da mesma instituição")
    void managerAccessesStudentWithDistinctInstitutionInstances() {
        UUID institutionId = UUID.randomUUID();
        InstitutionModel managerSide = InstitutionModel.builder().id(institutionId).build();
        InstitutionModel studentSide = InstitutionModel.builder().id(institutionId).build();
        InstitutionModel foreign = InstitutionModel.builder().id(UUID.randomUUID()).build();

        Manager manager = new Manager();
        manager.setId(UUID.randomUUID());
        manager.setInstitution(managerSide);
        Student sameInstitution = student(studentSide);
        Student otherInstitution = student(foreign);
        Student noInstitutionId = student(InstitutionModel.builder().build());

        when(userRepository.findById(sameInstitution.getId())).thenReturn(Optional.of(sameInstitution));
        when(userRepository.findById(otherInstitution.getId())).thenReturn(Optional.of(otherInstitution));
        when(userRepository.findById(noInstitutionId.getId())).thenReturn(Optional.of(noInstitutionId));
        loginAs(manager);

        org.junit.jupiter.api.Assertions.assertNotSame(managerSide, studentSide);
        assertTrue(guard.canAccess(sameInstitution.getId()));
        assertFalse(guard.canAccess(otherInstitution.getId()));
        assertFalse(guard.canAccess(noInstitutionId.getId()));
    }

    @Test
    @DisplayName("Gestor sem instituição ou aluno sem instituição nega o acesso")
    void missingInstitutionDeniesAccess() {
        Manager manager = new Manager();
        manager.setId(UUID.randomUUID());
        manager.setInstitution(InstitutionModel.builder().id(UUID.randomUUID()).build());
        Student withoutInstitution = student(null);
        when(userRepository.findById(withoutInstitution.getId())).thenReturn(Optional.of(withoutInstitution));
        loginAs(manager);

        assertFalse(guard.canAccess(withoutInstitution.getId()));

        Manager orphan = new Manager();
        orphan.setId(UUID.randomUUID());
        loginAs(orphan);
        assertFalse(guard.canAccess(UUID.randomUUID()));
    }

    @Test
    @DisplayName("Verifica o acesso negado a usuários não autenticados")
    void withoutLoginEverythingIsDenied() {
        // Arrange
        InstitutionModel institution = mock(InstitutionModel.class);
        when(institution.getId()).thenReturn(UUID.randomUUID());

        // Act e Assert
        assertFalse(guard.canAccess(UUID.randomUUID()));
        assertFalse(guard.isInstituicaoUsuario(institution.getId()));
    }

    private Student student(InstitutionModel institution) {
        Student student = new Student();
        student.setId(UUID.randomUUID());
        student.setInstitution(institution);
        return student;
    }

    private void loginAs(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}
