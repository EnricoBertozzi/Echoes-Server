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
    @DisplayName("Deve incrementar o contador após falha no login")
    void shouldIncrementFailedAttempts() {
        // Arrange
        User user = mock(User.class);

        when(attemptRepository.findUserByEmail(email)).thenReturn(Optional.of(user));
        when(user.getLoginAttempts()).thenReturn(0);
        when(user.getId()).thenReturn(userId);

        // Act
        attemptService.registerFailedAttempt(email);

        // Assert
        verify(attemptRepository).updateUserAttempts(userId, 1);
    }

    @Test
    @DisplayName("Deve acumular tentativas de autenticação")
    void shouldAccumulateFailedAttempts() {
        // Arrange
        User user = mock(User.class);

        when(attemptRepository.findUserByEmail(email)).thenReturn(Optional.of(user));
        when(user.getId()).thenReturn(userId);

        when(user.getLoginAttempts()).thenReturn(0, 1, 2, 3);

        // Act
        for (int i = 0; i < 4; i++) {
            attemptService.registerFailedAttempt(email);
        }

        // Assert
        verify(attemptRepository).updateUserAttempts(userId, 1);
        verify(attemptRepository).updateUserAttempts(userId, 2);
        verify(attemptRepository).updateUserAttempts(userId, 3);
        verify(attemptRepository).updateUserAttempts(userId, 4);

        verify(attemptRepository, never()).updateUserLockedUntil(any(UUID.class), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("Deve bloquear usuário ao atingir 5 tentativas")
    void shouldBlockUserAfterFiveAttempts() {
        // Arrange
        User user = mock(User.class);

        when(attemptRepository.findUserByEmail(email)).thenReturn(Optional.of(user));
        when(user.getLoginAttempts()).thenReturn(5);
        when(user.getId()).thenReturn(userId);
        when(user.getEmail()).thenReturn(email);

        LocalDateTime before = LocalDateTime.now();

        // Act
        attemptService.registerFailedAttempt(email);

        // Assert
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
        // Arrange
        User user = mock(User.class);

        when(attemptRepository.findUserByEmail(email)).thenReturn(Optional.of(user));
        when(user.getId()).thenReturn(userId);

        // Act
        attemptService.resetLoginAttempt(email);

        // Assert
        verify(attemptRepository).updateUserAttempts(userId, 0);
    }
}