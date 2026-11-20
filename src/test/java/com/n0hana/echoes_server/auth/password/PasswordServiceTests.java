package com.n0hana.echoes_server.auth.password;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
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

import com.n0hana.echoes_server.auth.attempt.AttemptScope;
import com.n0hana.echoes_server.auth.attempt.AttemptService;
import com.n0hana.echoes_server.auth.exception.AuthFailedException;
import com.n0hana.echoes_server.auth.exception.TooManyAttemptsException;
import com.n0hana.echoes_server.infra.notifier.NotifierService;
import com.n0hana.echoes_server.mfa.TwoFactorService;
import com.n0hana.echoes_server.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class PasswordServiceTests {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordCodeRepository codeRepository;
    @Mock
    private PasswordTokenRepository tokenRepository;
    @Mock
    private TwoFactorService twoFactorService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AttemptService attempts;
    @Mock
    private NotifierService notifier;

    @InjectMocks
    private PasswordService service;

    private final String email = "teste@email.com";

    @Test
    @DisplayName("Código certo devolve token, apaga o código e zera o contador")
    void validateSuccess() {
        when(codeRepository.findByEmail(email)).thenReturn(Optional.of("123456"));

        String token = service.validate(email, "123456");

        assertNotNull(token);
        verify(tokenRepository).save(email, token);
        verify(codeRepository).delete(email);
        verify(attempts).clear(AttemptScope.PASSWORD_VALIDATE, email);
    }

    @Test
    @DisplayName("Código errado conta tentativa e mantém o código enquanto não estoura")
    void validateWrongCodeKeepsCode() {
        when(codeRepository.findByEmail(email)).thenReturn(Optional.of("123456"));
        when(attempts.register(AttemptScope.PASSWORD_VALIDATE, email)).thenReturn(false);

        assertThrows(AuthFailedException.class, () -> service.validate(email, "000000"));

        verify(codeRepository, never()).delete(email);
        verify(tokenRepository, never()).save(anyString(), anyString());
    }

    @Test
    @DisplayName("Ao estourar o limite o código é apagado")
    void validateExhaustedDeletesCode() {
        when(codeRepository.findByEmail(email)).thenReturn(Optional.of("123456"));
        when(attempts.register(AttemptScope.PASSWORD_VALIDATE, email)).thenReturn(true);

        assertThrows(AuthFailedException.class, () -> service.validate(email, "000000"));

        verify(codeRepository).delete(email);
    }

    @Test
    @DisplayName("Sem código salvo também conta tentativa")
    void validateWithoutCodeRegistersAttempt() {
        when(codeRepository.findByEmail(email)).thenReturn(Optional.empty());

        assertThrows(AuthFailedException.class, () -> service.validate(email, "123456"));

        verify(attempts).register(AttemptScope.PASSWORD_VALIDATE, email);
    }

    @Test
    @DisplayName("Validação bloqueada lança 429 sem olhar o código salvo")
    void validateBlocked() {
        doThrow(new TooManyAttemptsException()).when(attempts)
                .assertNotBlocked(AttemptScope.PASSWORD_VALIDATE, email);

        assertThrows(TooManyAttemptsException.class, () -> service.validate(email, "123456"));

        verify(codeRepository, never()).findByEmail(anyString());
    }

    @Test
    @DisplayName("Pedido de reset conta tentativa mesmo para e-mail inexistente")
    void forgotCountsEvenWhenUserMissing() {
        when(userRepository.existsByEmail(email)).thenReturn(false);

        service.request(email);

        verify(attempts).register(AttemptScope.PASSWORD_FORGOT, email);
        verify(notifier, never()).sendPasswordReset(anyString(), anyString());
    }

    @Test
    @DisplayName("Pedido de reset envia o código quando o usuário existe")
    void forgotSendsCode() {
        when(userRepository.existsByEmail(email)).thenReturn(true);
        when(twoFactorService.generateCode()).thenReturn("123456");

        service.request(email);

        verify(codeRepository).save(email, "123456");
        verify(notifier).sendPasswordReset(email, "123456");
    }

    @Test
    @DisplayName("Pedido de reset bloqueado lança 429 e não envia e-mail")
    void forgotBlocked() {
        doThrow(new TooManyAttemptsException()).when(attempts)
                .assertNotBlocked(AttemptScope.PASSWORD_FORGOT, email);

        assertThrows(TooManyAttemptsException.class, () -> service.request(email));

        verify(notifier, never()).sendPasswordReset(anyString(), anyString());
        verify(userRepository, never()).existsByEmail(anyString());
    }

    @Test
    @DisplayName("Token de redefinição é apagado após o uso e a senha marca passwordChangedAt (S1/S2)")
    void resetConsumesTokenAndMarksPasswordChange() {
        com.n0hana.echoes_server.user.model.User user = org.mockito.Mockito.mock(com.n0hana.echoes_server.user.model.User.class);
        when(tokenRepository.consumeIfMatches(email, "token-ok")).thenReturn(true);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("Nova@Senha1")).thenReturn("hash");

        service.reset(email, "token-ok", "Nova@Senha1");

        verify(user).setPassword("hash");
        verify(user).setPasswordChangedAt(org.mockito.ArgumentMatchers.any(java.time.Instant.class));
    }

    @Test
    @DisplayName("Token errado não é aceito e não é consumido")
    void resetWithWrongTokenKeepsToken() {
        when(tokenRepository.consumeIfMatches(email, "outro")).thenReturn(false);

        org.junit.jupiter.api.Assertions.assertThrows(
                com.n0hana.echoes_server.auth.exception.AuthFailedException.class,
                () -> service.reset(email, "outro", "Nova@Senha1"));

        verify(tokenRepository, never()).delete(anyString());
        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    @DisplayName("Validate e reset normalizam o e-mail: caixa diferente usa a mesma chave do Redis")
    void passwordFlowIsCaseInsensitive() {
        when(codeRepository.findByEmail(email)).thenReturn(Optional.of("123456"));

        String token = service.validate("  TESTE@Email.com ", "123456");

        verify(attempts).assertNotBlocked(AttemptScope.PASSWORD_VALIDATE, email);
        verify(tokenRepository).save(email, token);

        com.n0hana.echoes_server.user.model.User user = org.mockito.Mockito.mock(com.n0hana.echoes_server.user.model.User.class);
        when(tokenRepository.consumeIfMatches(email, token)).thenReturn(true);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("Nova@Senha1")).thenReturn("hash");

        service.reset("Teste@EMAIL.com", token, "Nova@Senha1");

        verify(user).setPassword("hash");
    }

    @Test
    @DisplayName("Pedido de reset normaliza o e-mail antes de contar tentativa e gravar o código")
    void requestNormalizesEmail() {
        when(userRepository.existsByEmail(email)).thenReturn(true);
        when(twoFactorService.generateCode()).thenReturn("654321");

        service.request("TESTE@email.COM");

        verify(attempts).register(AttemptScope.PASSWORD_FORGOT, email);
        verify(codeRepository).save(email, "654321");
    }
}
