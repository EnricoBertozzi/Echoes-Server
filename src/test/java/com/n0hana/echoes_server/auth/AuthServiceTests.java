package com.n0hana.echoes_server.auth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.n0hana.echoes_server.auth.attempt.AttemptScope;
import com.n0hana.echoes_server.auth.attempt.AttemptService;
import com.n0hana.echoes_server.auth.exception.AuthFailedException;
import com.n0hana.echoes_server.auth.exception.TooManyAttemptsException;
import com.n0hana.echoes_server.auth.exception.InvalidMfaCodeException;
import com.n0hana.echoes_server.auth.exception.LogoutFailedException;
import com.n0hana.echoes_server.auth.exception.UserBlockedException;
import com.n0hana.echoes_server.auth.loginattempt.LoginAttemptService;
import com.n0hana.echoes_server.infra.notifier.NotifierService;
import com.n0hana.echoes_server.infra.security.JwtTokenService;
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

    @Mock
    private AttemptService mfaAttempts;

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
    @DisplayName("Conta bloqueada no login responde como senha errada, sem revelar o bloqueio (S8)")
    void shouldThrowUserBlockedExceptionWhenUserIsBlocked() {
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
    @DisplayName("Deve lançar a exceção InvalidMfaCodeException quando o código multifator estiver incorreto")
    void shouldThrowInvalidMfaCodeExceptionWhenCodeIsIncorrect() {
        String email = "teste@email.com";
        String savedCode = "123456";
        String wrongCode = "987654";

        when(authRepository.findByEmail(email)).thenReturn(Optional.of(savedCode));

        assertThrows(InvalidMfaCodeException.class, () -> authService.verifyMfaCode(email, wrongCode));
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
        assertThrows(UserBlockedException.class, () -> authService.verifyMfaCode(email, code));

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
        assertThrows(InvalidMfaCodeException.class, () -> authService.verifyMfaCode(email, wrongCode));

        verify(mfaAttempts).register(AttemptScope.MFA, email);
        verify(attemptService, never()).registerFailedAttempt(email);
        verify(jwtTokenService, never()).generate(any());
    }

    @Test
    @DisplayName("InvalidMfaCodeException deve ser AuthFailedException para não sofrer rollback no login (B1)")
    void invalidMfaCodeIsAuthFailed() {
        assertTrue(new InvalidMfaCodeException() instanceof AuthFailedException);
    }

    @Test
    @DisplayName("Código MFA deve ser apagado ao estourar o limite de tentativas (B1)")
    void shouldDeleteMfaCodeWhenAttemptLimitIsReached() {
        String email = "teste@email.com";
        when(authRepository.findByEmail(email)).thenReturn(Optional.of("123456"));
        when(mfaAttempts.register(AttemptScope.MFA, email)).thenReturn(true);

        assertThrows(InvalidMfaCodeException.class, () -> authService.verifyMfaCode(email, "000000"));

        verify(authRepository).delete(email);
    }

    @Test
    @DisplayName("Deve bloquear MFA sem consultar o código quando já estourou o limite (B1)")
    void shouldRejectMfaWhenAttemptsExhausted() {
        String email = "teste@email.com";
        doThrow(new TooManyAttemptsException()).when(mfaAttempts).assertNotBlocked(AttemptScope.MFA, email);

        assertThrows(TooManyAttemptsException.class, () -> authService.verifyMfaCode(email, "123456"));

        verifyNoInteractions(authRepository);
        verify(jwtTokenService, never()).generate(any());
    }

    @Test
    @DisplayName("Código MFA correto deve zerar o contador de tentativas")
    void shouldClearMfaAttemptsOnSuccess() {
        String email = "teste@email.com";
        User user = mock(User.class);
        when(authRepository.findByEmail(email)).thenReturn(Optional.of("123456"));
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(user.isAccountNonLocked()).thenReturn(true);
        when(user.isEnabled()).thenReturn(true);
        when(user.getEmail()).thenReturn(email);
        when(jwtTokenService.generate(user)).thenReturn("token");

        assertEquals("token", authService.verifyMfaCode(email, "123456"));

        verify(mfaAttempts).clear(AttemptScope.MFA, email);
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
        assertThrows(InvalidMfaCodeException.class, () -> authService.verifyMfaCode(email, wrongCode));

        verify(attemptService, times(1)).registerFailedAttempt(email);
        verify(mfaAttempts, times(1)).register(AttemptScope.MFA, email);
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
    @DisplayName("Deve alterar a senha do usuário com sucesso")
    void shouldSuccessfullyChangePassword() {
        // Arrange
        String email = "teste@email.com";
        User user = mock(User.class);
        String currentPassword = "senhaAtual";
        String newPassword = "novaSenha123";
        String encodedPassword = "senhaCriptografada";
        String newEncodedPassword = "novaSenhaCriptografada";

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(user.isAccountNonLocked()).thenReturn(true);
        when(user.isEnabled()).thenReturn(true);
        when(user.getPassword()).thenReturn(encodedPassword);
        when(passwordEncoder.matches(currentPassword, encodedPassword)).thenReturn(true);
        when(passwordEncoder.encode(newPassword)).thenReturn(newEncodedPassword);
        when(user.getEmail()).thenReturn(email);

        // Act
        assertDoesNotThrow(() -> authService.changePassword(email, currentPassword, newPassword));

        // Assert
        verify(userRepository).save(user);
        verify(passwordEncoder).encode(newPassword);
        verify(attemptService).resetLoginAttempt(email);
    }

    @Test
    @DisplayName("Deve lançar exceção AuthFailedException quando a senha atual estiver incorreta")
    void shouldThrowAuthFailedExceptionWhenCurrentPasswordIsIncorrect() {
        // Arrange
        String email = "teste@email.com";
        User user = mock(User.class);
        String currentPassword = "senhaAtual";
        String newPassword = "novaSenha123";
        String encodedPassword = "senhaCriptografada";

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(user.isAccountNonLocked()).thenReturn(true);
        when(user.isEnabled()).thenReturn(true);
        when(user.getPassword()).thenReturn(encodedPassword);
        when(passwordEncoder.matches(currentPassword, encodedPassword)).thenReturn(false);

        // Act e Assert
        assertThrows(AuthFailedException.class, () -> authService.changePassword(email, currentPassword, newPassword));

        // Assert
        verify(userRepository, never()).save(any(User.class));
        verify(attemptService, never()).resetLoginAttempt(anyString());
    }

    @Test
    @DisplayName("Deve impedir a alteração de senha quando o usuário estiver bloqueado")
    void shouldThrowUserBlockedExceptionWhenChangingPassword() {
        // Arrange
        String email = "teste@email.com";
        User user = mock(User.class);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(user.isAccountNonLocked()).thenReturn(false);

        // Act e Assert
        assertThrows(UserBlockedException.class, () -> authService.changePassword(email, "senhaAtual", "novaSenha123"));

        // Assert
        verify(userRepository, never()).save(any(User.class));
        verify(attemptService, never()).resetLoginAttempt(anyString());
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

    @Test
    @DisplayName("Login normaliza o e-mail (caixa e espaços) antes de consultar o banco e gravar o código no Redis")
    void loginNormalizesEmail() {
        User user = mock(User.class);
        when(userRepository.findByEmail("teste@email.com")).thenReturn(Optional.of(user));
        when(user.isAccountNonLocked()).thenReturn(true);
        when(user.isEnabled()).thenReturn(true);
        when(user.getPassword()).thenReturn("hash");
        when(passwordEncoder.matches("senha123", "hash")).thenReturn(true);
        when(twoFactorService.generateCode()).thenReturn("123456");

        authService.login("  Teste@Email.COM ", "senha123");

        verify(authRepository).save("teste@email.com", "123456");
        verify(notifier).sendMfaCode("teste@email.com", "123456");
    }

    @Test
    @DisplayName("MFA com e-mail em outra caixa usa a mesma chave do Redis e o mesmo contador")
    void verifyMfaNormalizesEmail() {
        User user = mock(User.class);
        when(authRepository.findByEmail("teste@email.com")).thenReturn(Optional.of("123456"));
        when(userRepository.findByEmail("teste@email.com")).thenReturn(Optional.of(user));
        when(user.isAccountNonLocked()).thenReturn(true);
        when(user.isEnabled()).thenReturn(true);
        when(jwtTokenService.generate(user)).thenReturn("jwt");

        assertEquals("jwt", authService.verifyMfaCode("TESTE@Email.com", "123456"));

        verify(mfaAttempts).assertNotBlocked(
                com.n0hana.echoes_server.auth.attempt.AttemptScope.MFA, "teste@email.com");
        verify(authRepository).delete("teste@email.com");
    }
}
