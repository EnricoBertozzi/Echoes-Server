package com.n0hana.echoes_server.auth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.n0hana.echoes_server.auth.exception.AuthFailedException;
import com.n0hana.echoes_server.auth.exception.LogoutFailedException;
import com.n0hana.echoes_server.auth.loginattempt.LoginAttemptService;
import com.n0hana.echoes_server.infra.notifier.NotifierService;
import com.n0hana.echoes_server.infra.security.JwtTokenService;
import com.n0hana.echoes_server.mfa.TwoFactorDTO;
import com.n0hana.echoes_server.mfa.TwoFactorService;
import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.model.User;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTests {

    @Mock
    private NotifierService notifier;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TwoFactorService twoFactorService;

    @Mock
    private AuthRepository authRepository;

    @Mock
    private JwtTokenService jwtTokenService;

    @Mock
    private LoginAttemptService attemptService;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("Deve realizar o login inicial com sucesso, enviando o código multifator estruturado")
    void shouldSuccessfullyLoginAndSubmitMfaCode() {
        User user = mock(User.class);

        String email = "teste@email.com";
        String code = "123456";
        String password = "senha123";
        String encodedPassword = "senhaCriptografada";

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(user.isAccountNonLocked()).thenReturn(true);
        when(user.getPassword()).thenReturn(encodedPassword);
        when(passwordEncoder.matches(password, encodedPassword)).thenReturn(true);
        when(twoFactorService.generateCode()).thenReturn(code);
        when(user.isAccountNonLocked()).thenReturn(true);
        when(user.isEnabled()).thenReturn(true);

        assertDoesNotThrow(() -> authService.login(email, password));

        verify(authRepository).save(email, code);

        verify(notifier).sendMfaCode(email, code);
    }

    @Test
    @DisplayName("Deve lançar exceção AuthFailedException quando o email não estiver cadastrado no sistema durante o login")
    void shouldThrowAuthFailedExceptionWhenUserNotFound() {
        String email = "teste@email.com";
        String password = "senha123";
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        assertThrows(AuthFailedException.class, () -> authService.login(email, password));
    }

    @Test
    @DisplayName("Deve lançar exceção AuthFailedException quando o usuário estiver bloqueado durante o login")
    void shouldThrowAuthFailedExceptionWhenUserIsBlocked() {
        String email = "teste@email.com";
        String password = "senha123";
        User user = mock(User.class);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(user.isAccountNonLocked()).thenReturn(false);

        assertThrows(AuthFailedException.class, () -> authService.login(email, password));
    }

    @Test
    @DisplayName("Deve lançar exceção AuthFailedException quando a senha estiver incorreta durante o login")
    void shouldThrowAuthFailedExceptionWhenPasswordIsIncorrect() {
        String email = "teste@email.com";
        String password = "senha123";
        String encodedPassword = "senhaCriptografada123";
        User user = mock(User.class);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(user.getPassword()).thenReturn(encodedPassword);
        when(user.isAccountNonLocked()).thenReturn(true);
        when(user.isEnabled()).thenReturn(true);

        when(passwordEncoder.matches(password, encodedPassword)).thenReturn(false);

        assertThrows(AuthFailedException.class, () -> authService.login(email, password));
    }

    @Test
    @DisplayName("Deve realizar a verificação do código multifator e retornar token JWT")
    void shouldSuccessfullyVerifyMfaCode() {
        String email = "teste@gmail.com";
        String code = "123456";
        String token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWUsImlhdCI6MTUxNjIzOTAyMn0.KMUFsIDTnFmyG3nMiGM6H9FNFUROf3wh7SmqJp-QV30";
        User user = mock(User.class);

        when(authRepository.findByEmail(email)).thenReturn(Optional.of(code));
        when(jwtTokenService.generate(user)).thenReturn(token);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(user.isAccountNonLocked()).thenReturn(true);
        when(user.isEnabled()).thenReturn(true);

        String result = authService.verifyMfaCode(email, code);

        assertEquals(token, result);
        verify(authRepository).delete(email);
        verify(jwtTokenService).generate(user);
    }

    @Test
    @DisplayName("Deve lançar a exceção AuthFailedException quando o usuário não tiver realizado o primeiro fator de login")
    void shouldThrowAuthFailedExceptionWhenCodeNotFound() {
        String email = "teste@email.com";
        String code = "123456";

        when(authRepository.findByEmail(email)).thenReturn(Optional.empty());

        assertThrows(AuthFailedException.class, () -> authService.verifyMfaCode(email, code));
    }

    @Test
    @DisplayName("Deve lançar a exceção AuthFailedException quando o código multifator estiver incorreto")
    void shouldThrowAuthFailedExceptionWhenCodeIsIncorrect() {
        String email = "teste@email.com";
        String savedCode = "123456";
        String wrongCode = "987654";

        when(authRepository.findByEmail(email)).thenReturn(Optional.of(savedCode));

        assertThrows(AuthFailedException.class, () -> authService.verifyMfaCode(email, wrongCode));
    }

    @Test
    @DisplayName("Deve impedir o login quando o usuário está bloqueado")
    void shouldFailedLoginWhenUserIsBlocked() {
        // Arrange
        String email = "teste@email.com";
        String password = "1234567";

        User user = mock(User.class);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(user.isAccountNonLocked()).thenReturn(false);

        // Act e Assert
        assertThrows(AuthFailedException.class, () -> authService.login(email, password));

    }

    @Test
    @DisplayName("Deve impedir MFA quando o usuário estiver bloqueado")
    void shouldRejectMfaWhenUserIsBlocked() {
        // Arrange
        String email = "teste@email.com";
        String code = "123456";

        User user = mock(User.class);

        when(authRepository.findByEmail(email)).thenReturn(Optional.of(code));
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(user.isAccountNonLocked()).thenReturn(false);

        // Act e Assert
        assertThrows(AuthFailedException.class, () -> authService.verifyMfaCode(email, code));

        verify(authRepository).delete(email);
        verify(attemptService, never()).resetLoginAttempt(anyString());
        verify(jwtTokenService, never()).generate(any());
    }

    @Test
    @DisplayName("Deve registrar tentativa quando a senha estiver incorreta")
    void shouldRegisterAttemptWhenPasswordIsIncorrect() {
        // Arrange
        String email = "teste@email.com";
        String password = "senhaErrada";
        String encodedPassword = "senhaCriptografada";

        User user = mock(User.class);

        when(userRepository.findByEmail(email))
                .thenReturn(Optional.of(user));

        when(user.isAccountNonLocked()).thenReturn(true);
        when(user.isEnabled()).thenReturn(true);
        when(user.getEmail()).thenReturn(email);
        when(user.getPassword()).thenReturn(encodedPassword);

        // Act e Assert
        assertThrows(AuthFailedException.class, () -> authService.login(email, password));

        verify(attemptService).registerFailedAttempt(email);
        verify(authRepository, never()).save(anyString(), anyString());
    }

    @Test
    @DisplayName("Deve registrar tentativa quando o código MFA estiver incorreto")
    void shouldRegisterAttemptWhenMfaCodeIsIncorrect() {
        // Arrange
        String email = "teste@email.com";
        String code = "123456";
        String wrongCode = "999999";

        when(authRepository.findByEmail(email)).thenReturn(Optional.of(code));

        // Act e Assert
        assertThrows(AuthFailedException.class, () -> authService.verifyMfaCode(email, wrongCode));

        verify(attemptService).registerFailedAttempt(email);
        verify(jwtTokenService, never()).generate(any());
    }

    @Test
    @DisplayName("Deve registrar falhas tanto no login quanto no MFA")
    void shouldRegisterAttemptsAcrossLoginAndMfa() {
        // Arrange
        String email = "teste@email.com";
        String password = "senhaErrada";
        String encryptedPassword = "senhaCriptografada";
        String code = "123456";
        String wrongCode = "999999";

        User user = mock(User.class);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        when(user.isAccountNonLocked()).thenReturn(true);
        when(user.isEnabled()).thenReturn(true);
        when(user.getPassword()).thenReturn(encryptedPassword);
        when(user.getEmail()).thenReturn(email);

        when(authRepository.findByEmail(email)).thenReturn(Optional.of(code));

        // Act e Assert
        assertThrows(AuthFailedException.class, () -> authService.login(email, password));
        assertThrows(AuthFailedException.class, () -> authService.verifyMfaCode(email, wrongCode));

        verify(attemptService, times(2)).registerFailedAttempt(email);
        verify(attemptService, never()).resetLoginAttempt(anyString());
    }

    @Test
    @DisplayName("Deve permitir login após o usuário ser desbloqueado")
    void shouldAllowLoginAfterAccountUnlock() {
        // Arrange
        String email = "teste@email.com";
        String password = "senha123";
        String encodedPassword = "hash";

        User user = mock(User.class);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        // Primeira consulta retorna false, segunda consulta retorna true
        when(user.isAccountNonLocked()).thenReturn(false, true);

        when(user.isEnabled()).thenReturn(true);
        when(user.getPassword()).thenReturn(encodedPassword);
        when(passwordEncoder.matches(password, encodedPassword)).thenReturn(true);
        when(twoFactorService.generateCode()).thenReturn("123456");

        // Act e Assert
        assertThrows(AuthFailedException.class, () -> authService.login(email, password));
        assertDoesNotThrow(() -> authService.login(email, password));

        verify(authRepository).save(email, "123456");
        verify(notifier).sendMfaCode(email, "123456");
    }

    @Test
    @DisplayName("Deve realizar logout com sucesso quando o header for válido")
    void shouldSuccessfullyLogout() {
        // Arrange
        String header = "Bearer token123";

        // Act
        assertDoesNotThrow(() -> authService.logout(header));

        // Assert
        verify(jwtTokenService).invalidate(header);
    }

    @Test
    @DisplayName("Deve lançar LogoutFailedException quando o header for inválido")
    void shouldThrowLogoutFailedExceptionWhenHeaderIsInvalid() {
        // Arrange
        String header = "token123";

        // Act
        assertThrows(LogoutFailedException.class, () -> authService.logout(header));
        assertThrows(LogoutFailedException.class, () -> authService.logout(null));

        // Assert
        verifyNoInteractions(jwtTokenService);
    }
}
