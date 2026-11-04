package com.n0hana.echoes_server.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.n0hana.echoes_server.auth.exception.AuthFailedException;
import com.n0hana.echoes_server.auth.exception.LogoutFailedException;
import com.n0hana.echoes_server.auth.exception.UserBlockedException;
import com.n0hana.echoes_server.auth.loginattempt.LoginAttemptService;
import com.n0hana.echoes_server.infra.logs.Auditable;
import com.n0hana.echoes_server.infra.notifier.NotifierService;
import com.n0hana.echoes_server.infra.security.JwtTokenService;
import com.n0hana.echoes_server.mfa.TwoFactorService;
import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.exception.UserNotFoundException;
import com.n0hana.echoes_server.user.model.User;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service para gerenciar autenticação de usuários.
 * 
 * @author Enrico Bertozzi
 * @since 0.1.1
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final NotifierService notifier;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final TwoFactorService twoFactorService;
    private final JwtTokenService jwtTokenService;
    private final AuthRepository authRepository;
    private final LoginAttemptService attemptService;

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
     */
    @Transactional(dontRollbackOn = AuthFailedException.class)
    @Auditable(action = "LOGIN_ATTEMPT", entity = "User")
    public void login(String email, String password) {
        // Verificação se o usuário existe
        User user = userRepository.findByEmail(email)
                .orElseThrow(AuthFailedException::new);

        // Verifica se o usuário não foi bloqueado
        if (!user.isAccountNonLocked() || !user.isEnabled())
            throw new UserBlockedException();

        // Guard Clause para verificação de senha
        if (!passwordEncoder.matches(password, user.getPassword())) {
            attemptService.registerFailedAttempt(user.getEmail());
            throw new AuthFailedException();
        }

        String code = twoFactorService.generateCode();
        authRepository.save(email, code);

        notifier.sendMfaCode(email, code);
    }

    /**
     * Verifica o código multifator enviado e gera o token JWT
     * 
     * @param email Email do usuário
     * @param code  Código multifator
     */

    @Transactional(dontRollbackOn = AuthFailedException.class)
    @Auditable(action = "LOGIN_SUCCESS)", entity = "User")
    public String verifyMfaCode(String email, String code) {
        String savedCode = authRepository.findByEmail(email)
                .orElseThrow(AuthFailedException::new);

        if (!savedCode.equals(code)) {
            attemptService.registerFailedAttempt(email);
            throw new AuthFailedException();
        }

        User user = userRepository.findByEmail(email).orElseThrow(UserNotFoundException::new);

        // Verifica se o usuário não foi bloqueado
        if (!user.isAccountNonLocked() || !user.isEnabled()) {
            authRepository.delete(email);
            throw new UserBlockedException();
        }

        attemptService.resetLoginAttempt(user.getEmail());

        authRepository.delete(email);
        return jwtTokenService.generate(user);
    }

    /**
     * Revoga os tokens JWT de um usuário
     * 
     * @param header Cabeçalho contendo o token de autenticação
     */
    @Auditable(action = "LOGOUT", entity = "User")
    public void logout(String header) {
        if (header == null || !header.startsWith("Bearer "))
            throw new LogoutFailedException();

        jwtTokenService.invalidate(header);
    }

}
