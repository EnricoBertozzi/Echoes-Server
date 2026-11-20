package com.n0hana.echoes_server.term;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.model.Teacher;

@ExtendWith(MockitoExtension.class)
class TermRevokeTests {

    @Mock
    private TermRepository termRepository;

    @Mock
    private UserTermAcceptanceRepository userTermAcceptanceRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private TermService termService;

    @Test
    @DisplayName("Revogar consentimento marca os aceites (sem apagar) e derruba sessões, mantendo a conta ativa (RF17)")
    void revokeMarksAcceptancesAndKeepsAccountActive() {
        Teacher user = new Teacher();
        user.setEmail("prof@example.com");
        UUID id = UUID.randomUUID();
        user.setId(id);
        user.setActive(true);

        termService.revoke(user);

        ArgumentCaptor<Instant> when = ArgumentCaptor.forClass(Instant.class);
        verify(userTermAcceptanceRepository).revokeAllByUserId(eq(id), when.capture());

        assertNotNull(user.getTokensRevokedAt());
        assertEquals(when.getValue(), user.getTokensRevokedAt());
        assertTrue(user.isActive(), "a conta deve continuar ativa; o bloqueio é pelo aceite pendente");
        verify(userRepository).save(user);
    }
}
