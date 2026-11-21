package com.n0hana.echoes_server.infra.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.model.Admin;
import com.n0hana.echoes_server.user.model.User;

class SuperUserInitializerTests {

    private final UserRepository repository = mock(UserRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private SuperUserInitializer initializer;

    @BeforeEach
    void setUp() {
        initializer = new SuperUserInitializer(repository, encoder);
        ReflectionTestUtils.setField(initializer, "adminEmail", "admin@echoes.local");
        ReflectionTestUtils.setField(initializer, "adminUsername", "Administrador");
    }

    private ApplicationRunner runner(String password) {
        ReflectionTestUtils.setField(initializer, "adminPassword", password);
        return initializer.initializeSuperUser();
    }

    @Test
    @DisplayName("Cria o ADMIN com a senha codificada quando ele ainda não existe")
    void createsAdmin() throws Exception {
        when(repository.existsByEmail("admin@echoes.local")).thenReturn(false);
        when(encoder.encode("Senha@Forte1")).thenReturn("hash");

        runner("Senha@Forte1").run(null);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(repository).save(saved.capture());
        assertEquals(Admin.class, saved.getValue().getClass());
        assertEquals("hash", saved.getValue().getPassword());
        assertEquals("admin@echoes.local", saved.getValue().getEmail());
        // RF07: o admin do seed precisa trocar a senha no primeiro acesso
        org.junit.jupiter.api.Assertions.assertTrue(saved.getValue().isMustChangePassword());
    }

    @Test
    @DisplayName("Senha fora da política aborta a subida e nada é gravado")
    void weakPasswordAborts() {
        when(repository.existsByEmail("admin@echoes.local")).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> runner("fraca").run(null));

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Admin já existente não é recriado nem exige senha válida")
    void existingAdminIsUntouched() throws Exception {
        when(repository.existsByEmail("admin@echoes.local")).thenReturn(true);

        runner("fraca").run(null);

        verify(repository, never()).save(any());
    }
}
