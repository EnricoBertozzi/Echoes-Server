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
