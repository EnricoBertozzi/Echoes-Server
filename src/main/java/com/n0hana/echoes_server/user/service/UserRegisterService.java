package com.n0hana.echoes_server.user.service;

import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Instant;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.n0hana.echoes_server.infra.logs.Auditable;
import com.n0hana.echoes_server.infra.notifier.NotifierService;
import com.n0hana.echoes_server.infra.redis.CodeType;
import com.n0hana.echoes_server.institution.InstitutionModel;
import com.n0hana.echoes_server.institution.InstitutionService;
import com.n0hana.echoes_server.mfa.TwoFactorService;
import com.n0hana.echoes_server.term.TermService;
import com.n0hana.echoes_server.user.PendingRegistration;
import com.n0hana.echoes_server.user.PendingRegistrationRepository;
import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.dto.CompleteRegistrationDTO;
import com.n0hana.echoes_server.user.dto.CreateInstitutionUserDTO;
import com.n0hana.echoes_server.user.dto.CreateUserDTO;
import com.n0hana.echoes_server.user.dto.PendingRegistrationDTO;
import com.n0hana.echoes_server.user.dto.UserDTO;
import com.n0hana.echoes_server.user.exception.EmailAlreadyInUseException;
import com.n0hana.echoes_server.user.exception.ExpiredTwoFactorCodeException;
import com.n0hana.echoes_server.user.exception.InvalidTwoFactorCodeException;
import com.n0hana.echoes_server.user.exception.RegistrationAlreadyCompletedException;
import com.n0hana.echoes_server.user.exception.UserNotFoundException;
import com.n0hana.echoes_server.user.model.Admin;
import com.n0hana.echoes_server.user.model.Manager;
import com.n0hana.echoes_server.user.model.Student;
import com.n0hana.echoes_server.user.model.Teacher;
import com.n0hana.echoes_server.user.model.User;
import com.n0hana.echoes_server.user.model.UserRole;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

/**
 * Serviço para cadastro de novos usuários na plataforma
 * 
 * @author Enrico Bertozzi
 */
@Service
@RequiredArgsConstructor
public class UserRegisterService {
    private static final int MAX_VERIFICATION_ATTEMPTS = 5;

    private final NotifierService notifierService;

    private final PasswordEncoder passwordEncoder;

    private final UserRepository userRepository;

    private final PendingRegistrationRepository pendingRepository;

    private final TwoFactorService mfaService;

    private final TermService termService;

    private final InstitutionService institutionService;

    /**
     * Envia convite para cadastro de administrador
     * 
     * @param dto Objeto com dados de cadastro do administrador
     */
    @Auditable(action = "CREATE", entity = "Admin")
    public PendingRegistrationDTO createAdmin(CreateUserDTO dto) {
        return invite(dto.name(), dto.email(), UserRole.ADMIN, null);
    }

    /**
     * Envia convite para cadastro de gestor
     * 
     * @param dto Objeto com dados de cadastro do gestor
     */
    @Auditable(action = "CREATE", entity = "Manager")
    public PendingRegistrationDTO createManager(CreateInstitutionUserDTO dto) {
        return invite(dto.name(), dto.email(), UserRole.MANAGER, dto.institutionId());
    }

    /**
     * Envia convite para cadastro de professor
     * 
     * @param dto Objeto com dados de cadastro do professor
     */
    @Auditable(action = "CREATE", entity = "Teacher")
    public PendingRegistrationDTO createTeacher(CreateInstitutionUserDTO dto) {
        return invite(dto.name(), dto.email(), UserRole.TEACHER, dto.institutionId());
    }

    /**
     * Envia convite para cadastro de aluno
     * 
     * @param dto Objeto com dados de cadastro do aluno
     */
    @Auditable(action = "CREATE", entity = "Student")
    public PendingRegistrationDTO createStudent(CreateInstitutionUserDTO dto) {
        return invite(dto.name(), dto.email(), UserRole.STUDENT, dto.institutionId());
    }

    /**
     * Completa o fluxo de registro de um usuário no sistema
     * 
     * @param dto Objeto com dados para completar o registro do usuário
     * 
     * @return Objeto com dados do usuário registrado
     */
    @Transactional
    @Auditable(action = "COMPLETE_REGISTRATION", entity = "User")
    public UserDTO completeRegistration(CompleteRegistrationDTO dto) {
        String email = normalizeEmail(dto.email());
        termService.validateRequiredAccepted(dto.acceptedTerms());

        PendingRegistration pending = pendingRepository.findByEmail(email)
                .orElseThrow(() -> noPendingRegistrationFor(email));

        if (pending.expiresAt().isBefore(Instant.now())) {
            pendingRepository.deleteByEmail(email);
            throw new ExpiredTwoFactorCodeException();

        }

        if (!pending.code().equals(dto.code())) {
            registerFailedAttempt(pending);
            throw new InvalidTwoFactorCodeException();
        }

        assertEmailAvailable(email);

        // Criptografia da senha do usuário
        User user = newUserFrom(pending, passwordEncoder.encode(dto.password()));

        User saved;
        try {
            saved = userRepository.save(user);
        } catch (DataIntegrityViolationException ex) {
            if (isEmailDuplicate(ex)) {
                throw new EmailAlreadyInUseException();
            }
            throw ex;
        }

        termService.registerInitialAcceptances(saved, dto.acceptedTerms());
        pendingRepository.deleteByEmail(email);
        return toDTO(saved);
    }

    // ============================================================================
    // METODOS AUXILIARES
    // ============================================================================

    /**
     * Envia convite de cadastro genérico para usuários
     * 
     * @param user          Objeto com dados do usuário
     * @param role          Role do usuário a ser cadastrado
     * @param institutionId Identificador da instituição do usuário
     */
    private PendingRegistrationDTO invite(String name, String rawEmail, UserRole role, UUID institutionId) {
        String email = normalizeEmail(rawEmail);
        assertEmailAvailable(email);

        PendingRegistration pending = new PendingRegistration(
                name,
                email,
                role.getName(),
                institutionId,
                mfaService.generateCode(),
                Instant.now().plus(CodeType.INVITE.getDuration()),
                0,
                Instant.now());

        // Reconvites de um mesmo e-mail pendente sobrescrevem o registro
        pendingRepository.save(pending);

        notifierService.sendRegisterInvite(email, pending.code(), email);

        return new PendingRegistrationDTO(name, email, role, institutionId);
    }

    /**
     * Verifica se o e-mail está disponível
     * 
     * @param email e-mail a ser verificado
     */
    private void assertEmailAvailable(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyInUseException();
        }
    }

    /**
     * Normaliza o e-mail enviado para manter sem espaços extras ou maiusculas
     * 
     * @param email E-mail a ser normalizazdo
     * @return E-mail normalizado
     */
    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    /**
     * Retorna exceções para caso o usuário já seja registrado ou não foi criado seu
     * convite
     * 
     * @param email E-mail do usuário a ser verficado
     * @return Exceções não checadas para erro de verificação do usuário
     */
    private RuntimeException noPendingRegistrationFor(String email) {
        if (userRepository.existsByEmail(email)) {
            return new RegistrationAlreadyCompletedException();
        }
        return new UserNotFoundException();
    }

    /**
     * Verifica se a exceção de violação de dados é referente a chave duplicada
     * 
     * @param ex Exceção de violação de estado do banco de dados emitida durante o
     *           cadastro
     * @return Se a exceção foi lançada por quebrar integridade de unicidade de
     *         email
     */
    private boolean isEmailDuplicate(DataIntegrityViolationException ex) {
        if (ex.getMostSpecificCause() instanceof SQLIntegrityConstraintViolationException sqlEx) {
            return sqlEx.getErrorCode() == 1062 || "23505".equals(sqlEx.getSQLState());
        }
        return false;
    }

    /**
     * Conta o número de tentativas para validar o código de registro, excluindo os
     * dados caso o número limite seja ultrapassado
     * 
     * @param pending Pedido de registro a ser penalizado
     */
    private void registerFailedAttempt(PendingRegistration pending) {
        int attempts = pending.attempts() + 1;
        if (attempts >= MAX_VERIFICATION_ATTEMPTS) {
            pendingRepository.deleteByEmail(pending.email());
            return;
        }
        pendingRepository.savePreservingTtl(new PendingRegistration(
                pending.name(),
                pending.email(),
                pending.role(),
                pending.institutionId(),
                pending.code(),
                pending.expiresAt(),
                attempts,
                pending.createdAt()));
    }

    /**
     * Realiza o cadastro no usuário no banco de dados conforme sua role
     * 
     * @param pending         Pedido de registro com dados do usuário a ser salvo
     * @param encodedPassword Senha criptografada
     * @return Usuário cadastrado
     */
    private User newUserFrom(PendingRegistration pending, String encodedPassword) {
        User user = switch (UserRole.valueOf(pending.role())) {
            case ADMIN -> new Admin();
            case MANAGER -> new Manager();
            case TEACHER -> new Teacher();
            case STUDENT -> new Student();
        };
        user.setName(pending.name());
        user.setEmail(pending.email());
        user.setPassword(encodedPassword);
        
        InstitutionModel institutionModel = null; 
        if (pending.institutionId() != null)
           institutionModel = institutionService.findById(pending.institutionId());

        if (user instanceof Manager manager) {
            manager.setInstitution(institutionModel);
        } else if (user instanceof Teacher teacher) {
            teacher.setInstitution(institutionModel);
        } else if (user instanceof Student student) {
            student.setInstitution(institutionModel);
        }
        return user;
    }

    /**
     * Converte um usuário para um objeto DTO
     * 
     * @param user Usuário a ser convertido
     * @return DTO com dados do usuário
     */
    private UserDTO toDTO(User user) {
        InstitutionModel institutionModel = this.institutionIdOf(user);
        return new UserDTO(
                user.getId(),
                user.getName(),
                user.getEmail(),
                institutionModel.getId(),
                user.getUserRole());
    }

    /**
     * Adiciona o id do usuário baseado na sua subclasse
     * 
     * @param user Instãncia de usuário
     * @return UUID da instituição.
     */
    private InstitutionModel institutionIdOf(User user) {
        if (user instanceof Manager manager) {
            return manager.getInstitution();
        }
        if (user instanceof Teacher teacher) {
            return teacher.getInstitution();
        }
        if (user instanceof Student student) {
            return student.getInstitution();
        }
        return null;
    }

}
