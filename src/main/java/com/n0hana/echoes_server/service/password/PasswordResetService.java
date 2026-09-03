package com.n0hana.echoes_server.service.password;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.n0hana.echoes_server.dto.PasswordDTO;
import com.n0hana.echoes_server.dto.TwoFactorDto;
import com.n0hana.echoes_server.model.User;
import com.n0hana.echoes_server.repository.UserRepository;
import com.n0hana.echoes_server.service.BlackListService;
import com.n0hana.echoes_server.service.auth.JwtTokenService;
import com.n0hana.echoes_server.service.logs.Auditable;
import com.n0hana.echoes_server.service.notifier.TwoFactorNotifier;
import com.n0hana.echoes_server.service.password.InMemoryPasswordCodeRepository.CodeType;
import com.n0hana.echoes_server.service.password.InMemoryPasswordCodeRepository.PasswordCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final TwoFactorNotifier emailNotifier;
    private final UserRepository userRepository;
    private final SecureRandom random = new SecureRandom();
    private final InMemoryPasswordCodeRepository codeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtService;
    private final BlackListService blackListService;

    private final Duration expireDuration = Duration.ofMinutes(5);

    @Auditable(action = "Solicitação de Redefinição de Senha", entity = "PASSWORD")
    public void requestReset(PasswordDTO.ForgotRequest dto) {
        Optional<User> opt = userRepository.findUserByEmail(dto.email());

        if (opt.isEmpty()) return;

        User user = opt.get();

        String code = this.generateRandomCode();

        PasswordCode passwordCode = new PasswordCode();
        passwordCode.setEmail(dto.email());
        passwordCode.setCode(code);
        passwordCode.setExpiredAt(Instant.now().plusSeconds(300));
        passwordCode.setType(CodeType.RESET);

        codeRepository.save(passwordCode);
        emailNotifier.send(new TwoFactorDto(
            user.getEmail(), code, Instant.now().plusSeconds(300)));
    }

    @Auditable(action = "Redefinição de Senha", entity = "PASSWORD")
    public void resetPassword(PasswordDTO.ResetRequest dto) {
        String code = dto.code();

        PasswordCode savedCode = Optional.ofNullable(codeRepository.getCode(dto.email()))
            .orElseThrow(() -> new RuntimeException("Nenhum código de redefinição encontrado"));

        if (!code.equals(savedCode.getCode())) {
               throw new RuntimeException("Código de Redefinição de Senha Incorreto");
        }

        if (savedCode.getExpiredAt().isBefore(Instant.now())) {
            throw new RuntimeException("Código Expirado");
        }

        if (!savedCode.getType().equals(CodeType.RESET)) {
            throw new RuntimeException("Código Incorreto");
        }

        if (!dto.newPassword().equals(dto.confirmPassword())) {
            throw new RuntimeException("As senhas não são identicas");
        }

        User user = userRepository.findUserByEmail(dto.email()).orElseThrow( () ->
            new RuntimeException("E-mail não cadastrado")
        );

        codeRepository.delete(dto.email());
        user.setPassword(passwordEncoder.encode(dto.newPassword()));
        userRepository.save(user);
    }

    @Auditable(action = "Solicitação de Alteração de Senha", entity = "PASSWORD")
    public String validatePassword(PasswordDTO.ValidateRequest dto, String token) {

        token = token.replace("Bearer ", "");
        
        UUID uuid = UUID.fromString(jwtService.validadeToken(token));

        User user = userRepository.findById(uuid).orElseThrow( () ->
            new RuntimeException("Usuário não encontrado")
        );

        String password = user.getPassword();
        if (!passwordEncoder.matches(dto.password(), password)) {
            throw new RuntimeException("Senha incorreta");
        } 

        return jwtService.generatePasswordChangeToken(user);
    }

    @Auditable(action = "Alteração de Senha", entity = "PASSWORD")
    public void changePassword(PasswordDTO.ChangeRequest dto) {
    
        if (!dto.newPassword().equals(dto.confirmPassword())) {
            throw new RuntimeException("As senhas não coincidem");
        }

        String token = dto.token();
        token = token.replace("Bearer ", "");
        String uuid = jwtService.validatePasswordChangeToken(token);

        if (blackListService.isRevokedToken(token)) {
            throw new RuntimeException("Token já utilizado");  
        }
    
        User user = userRepository.findById(UUID.fromString(uuid)).orElseThrow( () ->
            new RuntimeException("Usuário não encontrado")
        );
        user.setPassword(passwordEncoder.encode(dto.newPassword()));
        userRepository.save(user);

        blackListService.revokeToken(token, expireDuration);
    }

    private String generateRandomCode() {
        int code = 100000 + random.nextInt(900000);
        return String.valueOf(code); 
    }
    
}
