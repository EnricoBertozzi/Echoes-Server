package com.n0hana.echoes_server.infra.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.model.Admin;
import com.n0hana.echoes_server.user.model.Manager;
import com.n0hana.echoes_server.user.model.Student;
import com.n0hana.echoes_server.user.model.User;

@ExtendWith(MockitoExtension.class)
class AccessGuardTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AccessGuard guard;

    private final UUID institution = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void adminAccessesAnything() {
        loginAs(new Admin());

        assertTrue(guard.canAccess(UUID.randomUUID()));
        assertTrue(guard.isInstituicaoUsuario(other));
    }

    @Test
    void studentAccessesOnlyHimself() {
        Student me = student(institution);
        loginAs(me);

        assertTrue(guard.canAccess(me.getId()));
        assertFalse(guard.canAccess(UUID.randomUUID()));
    }

    @Test
    void managerAccessesOnlyOwnInstitution() {
        Manager manager = new Manager();
        manager.setId(UUID.randomUUID());
        manager.setInstitutionId(institution);
        Student mine = student(institution);
        Student foreign = student(other);
        when(userRepository.findById(mine.getId())).thenReturn(Optional.of(mine));
        when(userRepository.findById(foreign.getId())).thenReturn(Optional.of(foreign));
        loginAs(manager);

        assertTrue(guard.canAccess(mine.getId()));
        assertFalse(guard.canAccess(foreign.getId()));
        assertTrue(guard.isInstituicaoUsuario(institution));
        assertFalse(guard.isInstituicaoUsuario(other));
        assertFalse(guard.isInstituicaoUsuario(null));
    }

    @Test
    void withoutLoginEverythingIsDenied() {
        assertFalse(guard.canAccess(UUID.randomUUID()));
        assertFalse(guard.isInstituicaoUsuario(institution));
    }

    private Student student(UUID institutionId) {
        Student student = new Student();
        student.setId(UUID.randomUUID());
        student.setInstitutionId(institutionId);
        return student;
    }

    private void loginAs(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}
