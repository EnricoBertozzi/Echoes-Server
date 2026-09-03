package com.n0hana.echoes_server.service.auth;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.n0hana.echoes_server.dto.PendingRegisterDTO;
import com.n0hana.echoes_server.dto.RegisterRequestDTO;
import com.n0hana.echoes_server.dto.TwoFactorDto;
import com.n0hana.echoes_server.dto.VerifyDTO;
import com.n0hana.echoes_server.model.DocumentType;
import com.n0hana.echoes_server.model.User;
import com.n0hana.echoes_server.model.UserRole;
import com.n0hana.echoes_server.model.UserTermsAcceptance;
import com.n0hana.echoes_server.repository.TermsRepository;
import com.n0hana.echoes_server.repository.UserRepository;
import com.n0hana.echoes_server.repository.UserTermsAcceptanceRepository;
import com.n0hana.echoes_server.repository.memory.InMemoryTwoFactorRepository;
import com.n0hana.echoes_server.repository.memory.PendingRegisterRepository;
import com.n0hana.echoes_server.service.logs.Auditable;
import com.n0hana.echoes_server.service.notifier.TwoFactorNotifier;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RegisterService {
    
    private final UserRepository userRepository;
    private final TwoFactorService twoFactorService;
    private final TwoFactorNotifier notifier;
    private final InMemoryTwoFactorRepository twoFactorRepository;
    private final PasswordEncoder passwordEncoder;
    private final PendingRegisterRepository registerRepository;
    private final TermsRepository termsRepository;
    private final UserTermsAcceptanceRepository userTermsAcceptanceRepository;

    @Value("${email.teacher.sufix:@gmail.com}")
    private String teacherEmailSufix;

    @Value("${email.student.sufix:@alunos.umc.br}")
    private String studentEmailSufix;

    @Value("${email.admin.sufix:@outlook.com}")
    private String adminEmailSufix;

    public void pendingRegister(RegisterRequestDTO dto) {     
        String email = dto.email();
        
        if (email.endsWith(teacherEmailSufix)) 
            registerRequest(dto, UserRole.TEACHER);
        else if (email.endsWith(studentEmailSufix))
            registerRequest(dto, UserRole.STUDENT);
        else 
            throw new RuntimeException("E-mail ou Senha Inválido");
    }

    public void pendingRegisterAdmin(RegisterRequestDTO dto) {
        String email = dto.email();

        if (email.endsWith(adminEmailSufix)) 
            registerRequest(dto, UserRole.ADMIN);
        else 
            throw new RuntimeException("E-mail ou Senha Inválido");
    }

    @Auditable(action = "Envio dos dados para registro", entity = "REGISTER")
    private void registerRequest(RegisterRequestDTO dto, UserRole role) {
        
        // Verifica se o usuário já existe
        if (userRepository.findUserByEmail(dto.email()).isPresent())
            throw new RuntimeException("E-mail ou Senha Inválidos");

        // Gera código de 2FA
        String code = twoFactorService.generateCode();

        // Geração do Token
        TwoFactorDto token = new TwoFactorDto(
            dto.email(),
            code,
            // 5 Minutos de Duração
            Instant.now().plusSeconds(300)
        );
        
        // Encriptação da senha para salvamento da requisição
        String password = passwordEncoder.encode(dto.password());


        PendingRegisterDTO pendingDTO = new PendingRegisterDTO(
            dto.name(),
            dto.email(),
            password,
            role
        );

        // Salva os dados do registro
        registerRepository.save(pendingDTO);

        // Salva os dados do 2FA
        twoFactorRepository.save(token);

        // Envia o código
        notifier.send(token);
    }

    @Auditable(action = "Registro do usuário", entity = "REGISTER")
    public void registerMFA(VerifyDTO dto) {

        // Verifica se o código existe
        var tokenExists = twoFactorRepository.findByEmail(dto.email());
        if (tokenExists.isEmpty())
            throw new RuntimeException("Código Inválido");

        // Recolhe o código 
        var token = tokenExists.get();
        
        // Verifica se foi expirado
        if (token.expiresAt().isBefore(Instant.now()) || !token.code().equals(dto.code()))
            throw new RuntimeException("Código Inválido");


        // Recupera os dados de registro e verifica se existem
        PendingRegisterDTO registerDto = registerRepository.find(dto.email());
        if (registerDto == null)
            throw new RuntimeException("Código Inválido");

        // Cria o novo usuário
        User user = new User(
            registerDto.name(), 
            dto.email(), 
            registerDto.password(), 
            registerDto.role()
        );
    
        userRepository.save(user);

        // Auto-accepts Terms of Use when registering
        acceptTermsForUser(user);

        // Limpa o dados do usuário da memória
        twoFactorRepository.deleteByEmail(dto.email());
        registerRepository.delete(registerDto.email());
    }

    private void acceptTermsForUser(User user) {
        for (DocumentType type : DocumentType.values()) {
            termsRepository.findByTypeAndActiveTrue(type).ifPresent(terms -> {
                UserTermsAcceptance acceptance = new UserTermsAcceptance(user, terms);
                userTermsAcceptanceRepository.save(acceptance);
            });
        }
    }
}
