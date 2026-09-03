package com.n0hana.echoes_server.service.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDateTime;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.n0hana.echoes_server.dto.AuthRequestDTO;
import com.n0hana.echoes_server.dto.TwoFactorDto;
import com.n0hana.echoes_server.dto.VerifyDTO;
import com.n0hana.echoes_server.dto.UserProfileExportDTO;
import com.n0hana.echoes_server.model.User;
import com.n0hana.echoes_server.repository.TokenRepository;
import com.n0hana.echoes_server.repository.UserRepository;
import com.n0hana.echoes_server.repository.UserTermsAcceptanceRepository;
import com.n0hana.echoes_server.repository.memory.InMemoryTwoFactorRepository;
import com.n0hana.echoes_server.service.logs.Auditable;
import com.n0hana.echoes_server.service.notifier.TwoFactorNotifier;
import com.n0hana.echoes_server.service.ratelimit.LoginAttemptService;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {
    
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final LoginAttemptService loginAttemptService;
    private final AuthenticationManager authenticationManager;
    private final TwoFactorService twoFactorService;
    private final TwoFactorNotifier notifier;
    private final InMemoryTwoFactorRepository twoFactorRepository;
    private final JwtTokenService tokenService;
    private final TokenRepository tokenRepository;
    private final UserTermsAcceptanceRepository userTermsAcceptanceRepository;

    @Auditable(action = "Envio das credenciais para login", entity = "LOGIN")
    public void loginRequest(AuthRequestDTO dto) {
        // Verifica se o Usuário existe
        Optional<User> opt = userRepository.findUserByEmail(dto.email());
        
        if (opt.isEmpty()) 
            throw new RuntimeException("E-mail ou senha inválidos");

        User user = opt.get();

        // Verifica se a conta está bloqueada ou não
        if (!user.isAccountNonLocked()) {
            throw new RuntimeException("Usuário Bloqueado");
        }

        // Autenticação primária
        var usernamePassword = new UsernamePasswordAuthenticationToken(dto.email(), dto.password());
        
        /**
         * Valida as credenciais do usuário,
         * caso sejam invalidas, adiciona um
         * contandor de falhas consecutivas 
         * para bloqueio de tentativas de 
         * brute force.
        */
        try {
            // Tenta autenticar o usuário com email e senha
            System.out.println(passwordEncoder.matches(dto.password(), user.getPassword()));
            this.authenticationManager.authenticate(usernamePassword);
            loginAttemptService.loginSucceeded(dto.email());

        } catch (BadCredentialsException e) {
            // Resposta da autenticação inválida
            loginAttemptService.loginFailed(dto.email());
            throw new RuntimeException("E-mail ou senha inválidos");
        }

        // Criação do Código de 2FA
        String code = twoFactorService.generateCode();

        TwoFactorDto token = new TwoFactorDto(
            dto.email(),
            code,
            Instant.now().plusSeconds(300)
        );

        // Salva o código 2FA
        twoFactorRepository.save(token);

        // Envia Notificação
        notifier.send(token);
    }

    @Auditable(action = "Validação do código multi fator", entity = "LOGIN")
    public String login2fa(VerifyDTO dto) {
        var token = twoFactorRepository.findByEmail(dto.email()).orElseThrow(() ->
            new RuntimeException("Código Inválido")
        );

        if (token.expiresAt().isBefore(Instant.now()) || !token.code().equals(dto.code()))
            throw new RuntimeException("Código Inválido");

        User user = userRepository.findUserByEmail(dto.email()).orElseThrow(() ->
            new RuntimeException("Código Inválido")
        );

        String jwt = tokenService.generateToken(user);

        twoFactorRepository.deleteByEmail(dto.email());

        return jwt;
    }

    @Auditable(action = "Logout do usuário", entity = "LOGOUT")
    public void logout(HttpServletRequest request, HttpServletResponse response, String header) {
        String token = null;

        // API - Authorization header
        if (header != null && header.startsWith("Bearer ")) {
            token = header.replace("Bearer ", "");
        }

        // Browser - Cookie
        Cookie[] cookies = request.getCookies();
        
        if (token == null && cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals("access_token")) {
                    token = cookie.getValue();
                    break;
                }
            }
        }

        if (token == null) {
            throw new RuntimeException("Token Inválido");
        }

        String uuid = tokenService.validadeToken(token);

        User user = userRepository.findById(UUID.fromString(uuid)).orElseThrow(() ->
            new RuntimeException("Token Inválido")
        );

        // revoga tokens ativos
        tokenService.revokeAll(user);

        // remove cookie do navegador
        Cookie cookie = new Cookie("access_token", "");
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setPath("/");
        cookie.setMaxAge(0);

        response.addCookie(cookie);
    }

    @Auditable(action = "Delete account", entity = "USER")
    public void deleteAccount(User user) {
        userTermsAcceptanceRepository.deleteAll(user.getId());
        tokenRepository.deleteByUser(user);
        userRepository.delete(user);
    }

    @Auditable(action = "Solicitação de exclusão de conta", entity = "USER")
    public void requestDeleteAccount(User user) {
        String code = twoFactorService.generateCode();
        TwoFactorDto token = new TwoFactorDto(
            user.getEmail(),
            code,
            Instant.now().plusSeconds(300)
        );
        twoFactorRepository.save(token);
        notifier.send(token);
    }

    @Auditable(action = "Confirmação de exclusão de conta", entity = "USER")
    public void confirmDeleteAccount(User user, String code) {
        var token = twoFactorRepository.findByEmail(user.getEmail())
            .orElseThrow(() -> new RuntimeException("Código Inválido ou Expirado"));

        if (!token.code().equals(code))
            throw new RuntimeException("Código Inválido");

        twoFactorRepository.deleteByEmail(user.getEmail());

        userTermsAcceptanceRepository.deleteAll(user.getId());
        tokenRepository.deleteByUser(user);
        userRepository.delete(user);
    }

    @Transactional(readOnly = true)
    @Auditable(action = "Exportação de dados cadastrais", entity = "USER")
    public UserProfileExportDTO exportUserData(User user) {
        if (user == null) {
            throw new IllegalArgumentException("Usuário inválido para exportação de dados.");
        }

        return new UserProfileExportDTO(
            user.getName(),
            user.getEmail(),
            user.getRole().toString(),
            LocalDateTime.now()
        );
    }
}
