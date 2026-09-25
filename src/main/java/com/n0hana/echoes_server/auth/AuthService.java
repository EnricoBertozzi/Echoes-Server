package com.n0hana.echoes_server.auth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.n0hana.echoes_server.auth.exception.AuthFailedException;
import com.n0hana.echoes_server.infra.logs.Auditable;
import com.n0hana.echoes_server.infra.security.JwtTokenService;
import com.n0hana.echoes_server.mfa.TwoFactorDTO;
import com.n0hana.echoes_server.mfa.TwoFactorService;
import com.n0hana.echoes_server.notifier.TwoFactorNotifier;
import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.model.User;

/**
 * Service para gerenciar autenticação de usuários.
 * 
 * @author Enrico Bertozzi
 * @since 0.1.1
 */
@Service
public class AuthService {

    @Autowired
    private TwoFactorNotifier notifier;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TwoFactorService twoFactorService;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private AuthRepository authRepository;

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
    public void login(String email, String password) {
        // Verificação se o usuário existe
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthFailedException());

        // Verifica se o usuário não foi bloqueado
        if (!user.isAccountNonLocked())
            throw new AuthFailedException();

        // Guard Clause para verificação de senha
        if (!passwordEncoder.matches(password, user.getPassword()))
            // TODO adicionar ratelimit
            throw new AuthFailedException();

        String code = twoFactorService.generateCode();
        authRepository.save(email, code);

        notifier.send(new TwoFactorDTO(email, code, null));
    }

    /**
     * Verifica o código multifator enviado e gera o token JWT
     * 
     * @param email Email do usuário
     * @param code  Código multifator
     */
    @Auditable(action = "MFA", entity = "Auth")
    public String verifyMfaCode(String email, String code) {
        String savedCode = authRepository.findByEmail(email)
                .orElseThrow(() -> new AuthFailedException());

        if (!savedCode.equals(code))
            // TODO usar TwoFactorService para validar código
            throw new AuthFailedException();

        authRepository.delete(email);
        User user = userRepository.findByEmail(email).get();
        return jwtTokenService.generate(user);
    }

    /**
     * Revoga os tokens JWT de um usuário
     * 
     * @param header Cabeçalho contendo o token de autenticação
     */
    @Auditable(action = "LOGOUT", entity = "Auth")
    public void logout(String header) {
        if (header == null || !header.startsWith("Bearer "))
            // TODO Alterar exceção
            throw new AuthFailedException();

        jwtTokenService.invalidate(header);
    }

}
