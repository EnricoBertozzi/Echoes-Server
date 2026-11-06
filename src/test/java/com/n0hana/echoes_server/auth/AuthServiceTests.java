package com.n0hana.echoes_server.auth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

import com.n0hana.echoes_server.auth.attempt.AttemptScope;
import com.n0hana.echoes_server.auth.attempt.AttemptService;
import com.n0hana.echoes_server.auth.exception.AuthFailedException;
import com.n0hana.echoes_server.auth.exception.TooManyAttemptsException;
import com.n0hana.echoes_server.infra.security.JwtTokenService;
import com.n0hana.echoes_server.mfa.TwoFactorDTO;
import com.n0hana.echoes_server.mfa.TwoFactorService;
import com.n0hana.echoes_server.notifier.TwoFactorNotifier;
import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.model.User;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTests {

    @Mock
    private TwoFactorNotifier notifier;

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
    private AttemptService attempts;

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

        assertDoesNotThrow(() -> authService.login(email, password));

        verify(authRepository).save(email, code);

        ArgumentCaptor<TwoFactorDTO> captor = ArgumentCaptor.forClass(TwoFactorDTO.class);
        verify(notifier).send(captor.capture());

        TwoFactorDTO sentDto = captor.getValue();
        assertEquals(email, sentDto.email());
        assertEquals(code, sentDto.code());
        assertNotNull(sentDto.expiresAt());
        assertTrue(sentDto.expiresAt().isAfter(Instant.now()));
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
        when(user.isAccountNonLocked()).thenReturn(true);
        when(user.getPassword()).thenReturn(encodedPassword);
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
    @DisplayName("Login com senha errada deve contar uma tentativa")
    void shouldRegisterAttemptWhenPasswordIsIncorrect() {
        String email = "teste@email.com";
        User user = mock(User.class);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(user.isAccountNonLocked()).thenReturn(true);
        when(user.getPassword()).thenReturn("hash");
        when(passwordEncoder.matches("errada", "hash")).thenReturn(false);

        assertThrows(AuthFailedException.class, () -> authService.login(email, "errada"));

        verify(attempts).register(AttemptScope.LOGIN, email);
        verify(attempts, never()).clear(AttemptScope.LOGIN, email);
    }

    @Test
    @DisplayName("Login com e-mail inexistente também conta tentativa, igual a senha errada")
    void shouldRegisterAttemptWhenUserNotFound() {
        String email = "naoexiste@email.com";
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        assertThrows(AuthFailedException.class, () -> authService.login(email, "senha"));

        verify(attempts).register(AttemptScope.LOGIN, email);
    }

    @Test
    @DisplayName("Login bloqueado deve lançar 429 sem consultar o banco")
    void shouldThrowTooManyAttemptsWhenLoginBlocked() {
        String email = "teste@email.com";
        doThrow(new TooManyAttemptsException()).when(attempts).assertNotBlocked(AttemptScope.LOGIN, email);

        assertThrows(TooManyAttemptsException.class, () -> authService.login(email, "senha"));

        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    @DisplayName("Login com sucesso deve zerar o contador")
    void shouldClearAttemptsOnLoginSuccess() {
        String email = "teste@email.com";
        User user = mock(User.class);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(user.isAccountNonLocked()).thenReturn(true);
        when(user.getPassword()).thenReturn("hash");
        when(passwordEncoder.matches("senha", "hash")).thenReturn(true);
        when(twoFactorService.generateCode()).thenReturn("123456");

        authService.login(email, "senha");

        verify(attempts).clear(AttemptScope.LOGIN, email);
    }

    @Test
    @DisplayName("Código MFA errado conta tentativa e mantém o código enquanto não estoura o limite")
    void shouldRegisterAttemptAndKeepCodeWhenMfaIsWrong() {
        String email = "teste@email.com";
        when(authRepository.findByEmail(email)).thenReturn(Optional.of("123456"));
        when(attempts.register(AttemptScope.MFA, email)).thenReturn(false);

        assertThrows(AuthFailedException.class, () -> authService.verifyMfaCode(email, "000000"));

        verify(attempts).register(AttemptScope.MFA, email);
        verify(authRepository, never()).delete(email);
    }

    @Test
    @DisplayName("Ao atingir o limite de erros no MFA o código é apagado")
    void shouldDeleteMfaCodeWhenAttemptsExhausted() {
        String email = "teste@email.com";
        when(authRepository.findByEmail(email)).thenReturn(Optional.of("123456"));
        when(attempts.register(AttemptScope.MFA, email)).thenReturn(true);

        assertThrows(AuthFailedException.class, () -> authService.verifyMfaCode(email, "000000"));

        verify(authRepository).delete(email);
    }

    @Test
    @DisplayName("MFA bloqueado deve lançar 429 antes de olhar o código salvo")
    void shouldThrowTooManyAttemptsWhenMfaBlocked() {
        String email = "teste@email.com";
        doThrow(new TooManyAttemptsException()).when(attempts).assertNotBlocked(AttemptScope.MFA, email);

        assertThrows(TooManyAttemptsException.class, () -> authService.verifyMfaCode(email, "123456"));

        verify(authRepository, never()).findByEmail(anyString());
    }

    @Test
    @DisplayName("MFA correto deve zerar o contador")
    void shouldClearAttemptsOnMfaSuccess() {
        String email = "teste@gmail.com";
        User user = mock(User.class);

        when(authRepository.findByEmail(email)).thenReturn(Optional.of("123456"));
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(jwtTokenService.generate(user)).thenReturn("token");

        authService.verifyMfaCode(email, "123456");

        verify(attempts).clear(AttemptScope.MFA, email);
    }
}
