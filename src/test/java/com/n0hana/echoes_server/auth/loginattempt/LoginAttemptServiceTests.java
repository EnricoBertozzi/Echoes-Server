package com.n0hana.echoes_server.auth.loginattempt;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.n0hana.echoes_server.user.model.User;

@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTests {

    @Mock
    private LoginAttemptRepository attemptRepository;

    @InjectMocks
    private LoginAttemptService attemptService;

    private final String email = "teste@email.com";
    private final UUID userId = UUID.randomUUID();

    @Test
    @DisplayName("Deve incrementar o contador atomicamente após falha no login")
    void shouldIncrementFailedAttempts() {
        User user = mock(User.class);

        when(attemptRepository.findUserByEmail(email)).thenReturn(Optional.of(user));
        when(user.getId()).thenReturn(userId);
        when(attemptRepository.findUserAttempts(userId)).thenReturn(1);

        attemptService.registerFailedAttempt(email);

        verify(attemptRepository).incrementUserAttempts(userId);
        verify(attemptRepository, never()).updateUserLockedUntil(any(UUID.class), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("Deve acumular tentativas sem bloquear antes da 5ª falha")
    void shouldAccumulateFailedAttempts() {
        User user = mock(User.class);

        when(attemptRepository.findUserByEmail(email)).thenReturn(Optional.of(user));
        when(user.getId()).thenReturn(userId);
        when(attemptRepository.findUserAttempts(userId)).thenReturn(1, 2, 3, 4);

        for (int i = 0; i < 4; i++) {
            attemptService.registerFailedAttempt(email);
        }

        verify(attemptRepository, times(4)).incrementUserAttempts(userId);
        verify(attemptRepository, never()).updateUserLockedUntil(any(UUID.class), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("Deve bloquear o usuário já na 5ª falha (off-by-one corrigido, B5)")
    void shouldBlockUserOnFifthAttempt() {
        User user = mock(User.class);

        when(attemptRepository.findUserByEmail(email)).thenReturn(Optional.of(user));
        when(user.getId()).thenReturn(userId);
        when(user.getEmail()).thenReturn(email);
        when(attemptRepository.findUserAttempts(userId)).thenReturn(5);

        LocalDateTime before = LocalDateTime.now();
        attemptService.registerFailedAttempt(email);
        LocalDateTime after = LocalDateTime.now();

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(attemptRepository).updateUserLockedUntil(eq(userId), captor.capture());
        LocalDateTime lockedUntil = captor.getValue();

        assertFalse(lockedUntil.isBefore(before.plusHours(1)));
        assertFalse(lockedUntil.isAfter(after.plusHours(1)));
        verify(attemptRepository).updateUserAttempts(userId, 0);
    }

    @Test
    @DisplayName("Deve zerar tentativas após autenticação completa")
    void shouldResetAttempts() {
        User user = mock(User.class);

        when(attemptRepository.findUserByEmail(email)).thenReturn(Optional.of(user));
        when(user.getId()).thenReturn(userId);

        attemptService.resetLoginAttempt(email);

        verify(attemptRepository).updateUserAttempts(userId, 0);
    }
}
