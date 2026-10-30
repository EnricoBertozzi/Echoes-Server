package com.n0hana.echoes_server.auth.password;

import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.n0hana.echoes_server.auth.exception.AuthFailedException;
import com.n0hana.echoes_server.infra.notifier.NotifierService;
import com.n0hana.echoes_server.mfa.TwoFactorService;
import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.exception.UserNotFoundException;
import com.n0hana.echoes_server.user.model.User;

import jakarta.transaction.Transactional;

/**
 * Service para alteração de senhas dos usuários
 * 
 * @author Enrico Bertozzi
 * @since 0.1.1
 */
@Service
public class PasswordService {

    // TODO alterar todos os UserRepository por UserService
    private final UserRepository userRepository;

    private final PasswordCodeRepository codeRepository;

    private final PasswordTokenRepository tokenRepository;

    private final TwoFactorService twoFactorService;

    private final NotifierService notifier;

    private final PasswordEncoder passwordEncoder;

    PasswordService(UserRepository userRepository, PasswordCodeRepository codeRepository, PasswordTokenRepository tokenRepository, TwoFactorService twoFactorService, NotifierService notifier, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.codeRepository = codeRepository;
        this.tokenRepository = tokenRepository;
        this.twoFactorService = twoFactorService;
        this.notifier = notifier;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Cria a solicitação de redefinição de senha para confirmação do multifator
     * 
     * @param email Email do usuário
     */
    public void request(String email) {
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
    public String validate(String email, String code) {
        String savedCode = codeRepository.findByEmail(email)
                .orElseThrow(() -> new AuthFailedException());

        if (!savedCode.equals(code))
            throw new AuthFailedException();

        codeRepository.delete(email);

        String token = UUID.randomUUID().toString();
        tokenRepository.save(email, token);
        return token;
    }

    /**
     * Redefine a senha do usuário
     * 
     * @param email       Email do usuário
     * @param token       Token para redefinição
     * @param newPassword Nova senha do usuário
     */
    @Transactional
    public void reset(String email, String token, String newPassword) {
        // TODO alterar exceção

        String savedToken = tokenRepository.findByEmail(email)
                .orElseThrow(() -> new AuthFailedException());

        if (!savedToken.equals(token))
            throw new AuthFailedException();

        User user = userRepository.findByEmail(email).orElseThrow(() -> new UserNotFoundException());
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }
}
