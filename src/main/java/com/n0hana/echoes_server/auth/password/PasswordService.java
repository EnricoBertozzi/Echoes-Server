package com.n0hana.echoes_server.auth.password;

import com.n0hana.echoes_server.user.UserUtils;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.n0hana.echoes_server.auth.attempt.AttemptScope;
import com.n0hana.echoes_server.auth.attempt.AttemptService;
import com.n0hana.echoes_server.auth.exception.AuthFailedException;
import com.n0hana.echoes_server.infra.logs.Auditable;
import com.n0hana.echoes_server.infra.notifier.NotifierService;
import com.n0hana.echoes_server.mfa.TwoFactorService;
import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.exception.UserNotFoundException;
import com.n0hana.echoes_server.user.model.User;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

/**
 * Service para alteração de senhas dos usuários
 * 
 * @author Enrico Bertozzi
 * @since 0.1.1
 */
@Service
@RequiredArgsConstructor
public class PasswordService {

    private final UserRepository userRepository;

    private final PasswordCodeRepository codeRepository;

    private final PasswordTokenRepository tokenRepository;

    private final TwoFactorService twoFactorService;

    private final NotifierService notifier;

    private final PasswordEncoder passwordEncoder;

    private final AttemptService attempts;

    /**
     * Cria a solicitação de redefinição de senha para confirmação do multifator
     * 
     * @param email Email do usuário
     */
    @Auditable(action = "PASSWORD_FORGOT_REQUEST", entity = "User")
    public void request(String email) {
        email = UserUtils.normalizeEmail(email);
        // conta antes de olhar o banco: e-mail que existe e que não existe se comportam igual
        attempts.assertNotBlocked(AttemptScope.PASSWORD_FORGOT, email);
        attempts.register(AttemptScope.PASSWORD_FORGOT, email);

        if (!userRepository.existsByEmail(email))
            return;

        String code = twoFactorService.generateCode();

        codeRepository.save(email, code);
        notifier.sendPasswordReset(email, code);
    }

    /**
     * Validação do código multifator de redefinição de senha
     * 
     * @param code Código para verificação
     */
    @Auditable(action = "PASSWORD_CODE_VALIDATE", entity = "User")
    public String validate(String email, String code) {
        email = UserUtils.normalizeEmail(email);
        attempts.assertNotBlocked(AttemptScope.PASSWORD_VALIDATE, email);

        String savedCode = codeRepository.findByEmail(email).orElse(null);

        if (savedCode == null || !sameCode(savedCode, code)) {
            // no limite o código morre e o contador segue valendo, pedir outro não libera
            if (attempts.register(AttemptScope.PASSWORD_VALIDATE, email))
                codeRepository.delete(email);
            throw new AuthFailedException();
        }

        codeRepository.delete(email);
        attempts.clear(AttemptScope.PASSWORD_VALIDATE, email);

        String token = UUID.randomUUID().toString();
        tokenRepository.save(email, token);
        return token;
    }

    // compara em tempo constante pra não vazar acerto por tempo de resposta
    private boolean sameCode(String saved, String received) {
        return received != null && MessageDigest.isEqual(
                saved.getBytes(StandardCharsets.UTF_8),
                received.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Redefine a senha do usuário
     * 
     * @param email       Email do usuário
     * @param token       Token para redefinição
     * @param newPassword Nova senha do usuário
     */
    @Transactional
    @Auditable(action = "RESET_PASSWORD", entity = "User", details = "'Senha redefinida para ' + T(com.n0hana.echoes_server.user.UserUtils).maskEmail(#email)")
    public void reset(String email, String token, String newPassword) {
        email = UserUtils.normalizeEmail(email);
        // uso único (S1/PSI 2.4): comparação e remoção atômicas no Redis; token errado não apaga o válido
        if (!tokenRepository.consumeIfMatches(email, token))
            throw new AuthFailedException();

        User user = userRepository.findByEmail(email).orElseThrow(UserNotFoundException::new);
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(Instant.now());
        userRepository.save(user);
    }
}
