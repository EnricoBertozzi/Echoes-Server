package com.n0hana.echoes_server.auth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.n0hana.echoes_server.auth.exception.AuthFailedException;
import com.n0hana.echoes_server.infra.security.JwtTokenService;
import com.n0hana.echoes_server.mfa.TwoFactorService;
import com.n0hana.echoes_server.notifier.EmailLoginNotifier;
import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.model.User;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTests {

    @Mock
    private EmailLoginNotifier notifier;   // ← classe concreta, bate com AuthService

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

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("Deve realizar o o login inicial com sucesso, enviando o código multifator")
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
        verify(notifier).send(eq(code), eq(email));   // ← assinatura real: send(String, String)
    }

    @Test
    @DisplayName("Deve lançar exceção AuthFailedExcpetion quando o email não estiver cadastrados no sistema durante o login")
    void shouldThrowAuthFailedExceptionWhenUserNotFound() {
        String email = "teste@email.com";
        String password = "senha123";
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        assertThrows(AuthFailedException.class, () -> authService.login(email, password));
    }

    @Test
    @DisplayName("Deve lançar exceção AuthFailedExcpetion quando o usuário estiver bloqueado durante o login")
    void shouldThrowAuthFailedExceptionWhenUserIsBlocked() {
        String email = "teste@email.com";
        String password = "senha123";
        User user = mock(User.class);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(user.isAccountNonLocked()).thenReturn(true);

        assertThrows(AuthFailedException.class, () -> authService.login(email, password));
    }

    @Test
    @DisplayName("Deve lançar exceção AuthFailedExcpetion quando a senha estiver incorreta durante o login")
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
    @DisplayName("Deve lançar a exceção AuthFailedException quando o usuário não ter realizado o primeiro fator de login")
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
}
