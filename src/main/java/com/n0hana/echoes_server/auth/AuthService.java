package com.n0hana.echoes_server.auth;

import com.n0hana.echoes_server.user.UserUtils;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.n0hana.echoes_server.auth.attempt.AttemptScope;
import com.n0hana.echoes_server.auth.attempt.AttemptService;
import com.n0hana.echoes_server.auth.device.KnownDeviceService;
import com.n0hana.echoes_server.auth.exception.AuthFailedException;
import com.n0hana.echoes_server.infra.web.ClientIpResolver;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;
import com.n0hana.echoes_server.auth.exception.InvalidMfaCodeException;
import com.n0hana.echoes_server.auth.exception.LogoutFailedException;
import com.n0hana.echoes_server.auth.exception.UserBlockedException;
import com.n0hana.echoes_server.auth.exception.PasswordUnchangedException;
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
    private final AttemptService mfaAttempts;
    private final KnownDeviceService knownDevices;

    @org.springframework.beans.factory.annotation.Value("${api.security.trust-forwarded-for:false}")
    private boolean trustForwardedFor;

    @org.springframework.beans.factory.annotation.Value("${api.security.trusted-proxies:}")
    private String trustedProxies;

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
        email = UserUtils.normalizeEmail(email);
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            passwordEncoder.matches(password, dummyHash());
            throw new AuthFailedException();
        }

        if (!user.isAccountNonLocked() || !user.isEnabled()) {
            passwordEncoder.matches(password, user.getPassword());
            throw new AuthFailedException();
        }

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
    @Auditable(action = "LOGIN_SUCCESS", entity = "User")
    public String verifyMfaCode(String email, String code) {
        email = UserUtils.normalizeEmail(email);
        mfaAttempts.assertNotBlocked(AttemptScope.MFA, email);

        String savedCode = authRepository.findByEmail(email)
                .orElseThrow(AuthFailedException::new);

        if (!sameCode(savedCode, code)) {
            // ao estourar o limite o código morre: o usuário precisa logar de novo
            if (mfaAttempts.register(AttemptScope.MFA, email)) {
                authRepository.delete(email);
            }
            throw new InvalidMfaCodeException();
        }

        User user = userRepository.findByEmail(email).orElseThrow(UserNotFoundException::new);

        // Verifica se o usuário não foi bloqueado
        if (!user.isAccountNonLocked() || !user.isEnabled()) {
            authRepository.delete(email);
            throw new UserBlockedException();
        }

        attemptService.resetLoginAttempt(user.getEmail());
        mfaAttempts.clear(AttemptScope.MFA, email);

        authRepository.delete(email);
        alertIfNewDevice(user);
        return jwtTokenService.generate(user);
    }

    private void alertIfNewDevice(User user) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes))
            return;

        HttpServletRequest request = attributes.getRequest();
        String userAgent = request.getHeader("User-Agent");

        if (knownDevices.registerAndCheckNew(user.getId(), userAgent)) {
            notifier.sendNewDeviceLogin(
                    user.getEmail(),
                    ClientIpResolver.resolve(request, trustForwardedFor, trustedProxies),
                    userAgent,
                    Instant.now());
        }
    }

    /** hash de referência para equalizar o tempo de resposta quando a conta não existe */
    private volatile String dummyHash;

    private String dummyHash() {
        if (dummyHash == null) {
            dummyHash = passwordEncoder.encode("echoes-dummy-password");
        }
        return dummyHash;
    }

    private boolean sameCode(String saved, String received) {
        return received != null && MessageDigest.isEqual(
                saved.getBytes(StandardCharsets.UTF_8),
                received.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Altera a senha do usuário autenticado
     * 
     * @param email       Email do usuário
     * @param currentPassword Senha atual do usuário
     * @param newPassword Nova senha do usuário
     */
    @Transactional
    @Auditable(action = "PASSWORD_CHANGE", entity = "User")
    public void changePassword(
            String email,
            String currentPassword,
            String newPassword
    ) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(UserNotFoundException::new);

        if (!user.isAccountNonLocked() || !user.isEnabled()) {
            throw new UserBlockedException();
        }

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new AuthFailedException();
        }

        // no primeiro acesso a "troca" para a mesma senha anularia o objetivo da flag
        if (user.isMustChangePassword() && passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new PasswordUnchangedException();
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(Instant.now());
        user.setMustChangePassword(false);
        userRepository.save(user);

        attemptService.resetLoginAttempt(user.getEmail());
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
