package com.n0hana.echoes_server.auth.password;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.n0hana.echoes_server.auth.exception.AuthFailedException;
import com.n0hana.echoes_server.mfa.TwoFactorDTO;
import com.n0hana.echoes_server.mfa.TwoFactorService;
import com.n0hana.echoes_server.notifier.TwoFactorNotifier;
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
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordCodeRepository codeRepository;

    @Autowired
    private TwoFactorService twoFactorService;

    @Autowired
    private TwoFactorNotifier notifier;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * Cria a solicitação de redefinição de senha para confirmação do multifator
     * 
     * @param email Email do usuário
     */
    public void request(String email) {
        if (!userRepository.existsByEmail(email))
            throw new UserNotFoundException();

        String code = twoFactorService.generateCode();
        PasswordCodeModel model = new PasswordCodeModel(code);
        codeRepository.save(email, model);
        notifier.send(new TwoFactorDTO(email, code, null));
    }

    /**
     * Redefine a senha do usuário
     * 
     * @param email       Email do usuário
     * @param newPassword Nova senha do usuário
     */
    @Transactional
    public void reset(String email, String code, String newPassword) {
        // TODO alterar exceção
        PasswordCodeModel savedCode = (PasswordCodeModel) codeRepository.findByEmail(email)
                .orElseThrow(() -> new AuthFailedException());

        if (!savedCode.isValidated() || !savedCode.getCode().equals(code))
            throw new AuthFailedException();

        codeRepository.delete(email);
        User user = userRepository.findByEmail(email).orElseThrow(() -> new UserNotFoundException());
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    /**
     * Validação do código multifator de redefinição de senha
     * 
     * @param code Código para verificação
     */
    public void validate(String email, String code) {
        PasswordCodeModel savedCode = (PasswordCodeModel) codeRepository.findByEmail(email)
                .orElseThrow(() -> new AuthFailedException());

        if (!savedCode.getCode().equals(code))
            throw new AuthFailedException();

        savedCode.setValidated(true);
        codeRepository.save(email, savedCode);
    }

}
