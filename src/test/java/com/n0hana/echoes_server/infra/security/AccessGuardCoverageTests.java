package com.n0hana.echoes_server.infra.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.n0hana.echoes_server.institution.InstitutionModel;
import com.n0hana.echoes_server.institution.InstitutionRepository;
import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.model.Admin;
import com.n0hana.echoes_server.user.model.Manager;
import com.n0hana.echoes_server.user.model.Student;
import com.n0hana.echoes_server.user.model.Teacher;
import com.n0hana.echoes_server.user.model.User;

@DisplayName("AccessGuard: ramos de autorização")
class AccessGuardCoverageTests {

    private UserRepository users;
    private InstitutionRepository institutions;
    private AccessGuard guard;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        institutions = mock(InstitutionRepository.class);
        guard = new AccessGuard(users, institutions);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private InstitutionModel inst(UUID id) {
        InstitutionModel i = new InstitutionModel();
        i.setId(id);
        return i;
    }

    private <T extends User> T withId(T user) {
        user.setId(UUID.randomUUID());
        return user;
    }

    private void login(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private void loginPrincipal(Object principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @Test
    @DisplayName("canAccess: professor acessa a si mesmo, mas não a outro")
    void professor() {
        Teacher t = withId(new Teacher(inst(UUID.randomUUID())));
        login(t);

        assertTrue(guard.canAccess(t.getId()));
        assertFalse(guard.canAccess(UUID.randomUUID()));
        verifyNoInteractions(users);
    }

    @Test
    @DisplayName("canAccess: principal que não é User ou alvo nulo é negado")
    void principalInvalidoOuAlvoNulo() {
        loginPrincipal("anonymousUser");
        assertFalse(guard.canAccess(UUID.randomUUID()));

        Student s = withId(new Student(inst(UUID.randomUUID())));
        login(s);
        assertFalse(guard.canAccess(null));
    }

    @Test
    @DisplayName("canAccess: gestor sem instituição é negado")
    void gestorSemInstituicao() {
        Manager m = withId(new Manager(null));
        login(m);

        assertFalse(guard.canAccess(UUID.randomUUID()));
        verifyNoInteractions(users);
    }

    @Test
    @DisplayName("canAccess: gestor vê usuário da mesma instituição (por id), nega outra, nega inexistente e admin")
    void gestorVsAlvos() {
        UUID instId = UUID.randomUUID();
        Manager m = withId(new Manager(inst(instId)));
        login(m);

        Student mesma = withId(new Student(inst(instId)));
        Student outra = withId(new Student(inst(UUID.randomUUID())));
        Teacher semId = withId(new Teacher(inst(null)));
        Admin admin = withId(new Admin());
        when(users.findById(mesma.getId())).thenReturn(Optional.of(mesma));
        when(users.findById(outra.getId())).thenReturn(Optional.of(outra));
        when(users.findById(semId.getId())).thenReturn(Optional.of(semId));
        when(users.findById(admin.getId())).thenReturn(Optional.of(admin));

        assertTrue(guard.canAccess(mesma.getId()));
        assertFalse(guard.canAccess(outra.getId()));
        assertFalse(guard.canAccess(semId.getId()));
        assertFalse(guard.canAccess(admin.getId()));
        assertFalse(guard.canAccess(UUID.randomUUID()));
    }

    @Test
    @DisplayName("isInstituicaoUsuario: admin sempre passa")
    void adminInstituicao() {
        login(withId(new Admin()));

        assertTrue(guard.isInstituicaoUsuario(UUID.randomUUID()));
        verifyNoInteractions(institutions);
    }

    @Test
    @DisplayName("isInstituicaoUsuario: sem login, principal inválido ou id nulo é negado")
    void instituicaoNegada() {
        assertFalse(guard.isInstituicaoUsuario(UUID.randomUUID()));

        loginPrincipal("anonymousUser");
        assertFalse(guard.isInstituicaoUsuario(UUID.randomUUID()));

        login(withId(new Student(inst(UUID.randomUUID()))));
        assertFalse(guard.isInstituicaoUsuario(null));
    }

    @Test
    @DisplayName("isInstituicaoUsuario: usuário sem instituição é negado")
    void usuarioSemInstituicao() {
        login(withId(new Teacher(null)));

        assertFalse(guard.isInstituicaoUsuario(UUID.randomUUID()));
        verifyNoInteractions(institutions);
    }

    @Test
    @DisplayName("isInstituicaoUsuario: compara a instituição pedida com a do usuário")
    void compara() {
        UUID instId = UUID.randomUUID();
        UUID outraId = UUID.randomUUID();
        login(withId(new Student(inst(instId))));
        when(institutions.findById(instId)).thenReturn(Optional.of(inst(instId)));
        when(institutions.findById(outraId)).thenReturn(Optional.of(inst(outraId)));
        UUID inexistente = UUID.randomUUID();
        when(institutions.findById(inexistente)).thenReturn(Optional.empty());

        assertTrue(guard.isInstituicaoUsuario(instId));
        assertFalse(guard.isInstituicaoUsuario(outraId));
        assertFalse(guard.isInstituicaoUsuario(inexistente));
    }
}
