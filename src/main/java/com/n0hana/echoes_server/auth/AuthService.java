package com.n0hana.echoes_server.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.n0hana.echoes_server.auth.attempt.AttemptScope;
import com.n0hana.echoes_server.auth.attempt.AttemptService;
import com.n0hana.echoes_server.auth.exception.AuthFailedException;
import com.n0hana.echoes_server.infra.logs.Auditable;
import com.n0hana.echoes_server.infra.security.JwtTokenService;
import com.n0hana.echoes_server.mfa.TwoFactorDTO;
import com.n0hana.echoes_server.mfa.TwoFactorService;
import com.n0hana.echoes_server.notifier.TwoFactorNotifier;
import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.model.User;

import lombok.RequiredArgsConstructor;

/**
 * Service para gerenciar autenticação de usuários.
 * 
 * @author Enrico Bertozzi
 * @since 0.1.1
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final TwoFactorNotifier notifier;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final TwoFactorService twoFactorService;
    private final JwtTokenService jwtTokenService;
    private final AuthRepository authRepository;
    private final AttemptService attempts;

    /**
     * Realiza verificação do login do usuário
     * 
     * <p>
     * O método valida o email e senha informado do usuáriro para realizar o envio
     * do código multifator. Verifica existencia do usuário no sistema, se o usuário
     * não está bloqueado, e se a senha está correta, finalizando com envio do
     * código de multifator para o email do usuário. Caso alguma verificação falhe,
     * é lançado a exceção {@link AuthFailedException}
     * </p>
     * 
     * @param email    Email do usuário
     * @param password Senha do usuário
     * 
     * @throws {@link AuthFailedException}
     * @throws {@link TooManyAttemptsException} após 5 falhas em 15 minutos
     */
    @Auditable(action = "LOGIN_ATTEMPT", entity = "User")
    public void login(String email, String password) {
        // estourou o limite, nem consulta o banco
        attempts.assertNotBlocked(AttemptScope.LOGIN, email);

        // e-mail inexistente, conta bloqueada e senha errada contam igual e não dizem qual foi
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null
                || !user.isAccountNonLocked()
                || !passwordEncoder.matches(password, user.getPassword())) {
            attempts.register(AttemptScope.LOGIN, email);
            throw new AuthFailedException();
        }

        attempts.clear(AttemptScope.LOGIN, email);

        String code = twoFactorService.generateCode();
        authRepository.save(email, code);

        notifier.send(new TwoFactorDTO(email, code, Instant.now().plus(Duration.ofMinutes(15))));
    }

    /**
     * Verifica o código multifator enviado e gera o token JWT
     * 
     * @param email Email do usuário
     * @param code  Código multifator
     * 
     * @throws {@link AuthFailedException}
     * @throws {@link TooManyAttemptsException} após 5 erros em 15 minutos
     */

    @Auditable(action = "LOGIN_SUCESS)", entity = "User")
    public String verifyMfaCode(String email, String code) {
        attempts.assertNotBlocked(AttemptScope.MFA, email);

        String savedCode = authRepository.findByEmail(email).orElse(null);

        if (savedCode == null || !sameCode(savedCode, code)) {
            // no limite o código morre e o contador segue valendo, então só um novo login libera
            if (attempts.register(AttemptScope.MFA, email))
                authRepository.delete(email);
            throw new AuthFailedException();
        }

        authRepository.delete(email);
        attempts.clear(AttemptScope.MFA, email);
        User user = userRepository.findByEmail(email).get();
        return jwtTokenService.generate(user);
    }

    // compara em tempo constante pra não vazar acerto por tempo de resposta
    private boolean sameCode(String saved, String received) {
        return received != null && MessageDigest.isEqual(
                saved.getBytes(StandardCharsets.UTF_8),
                received.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Revoga os tokens JWT de um usuário
     * 
     * @param header Cabeçalho contendo o token de autenticação
     */
    @Auditable(action = "LOGOUT", entity = "User")
    public void logout(String header) {
        if (header == null || !header.startsWith("Bearer "))
            // TODO Alterar exceção
            throw new AuthFailedException();

        jwtTokenService.invalidate(header);
    }

}
