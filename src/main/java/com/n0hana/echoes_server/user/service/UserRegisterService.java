
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
import com.n0hana.echoes_server.user.UserUtils;

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
 * Serviço para cadastro de novos usuários na plataforma.
 *
 * @author Enrico Bertozzi
 */
@Service
@RequiredArgsConstructor
public class UserRegisterService {

    public static final int MAX_VERIFICATION_ATTEMPTS = 5;

    private final NotifierService notifierService;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final PendingRegistrationRepository pendingRepository;
    private final TwoFactorService mfaService;
    private final TermService termService;
    private final InstitutionService institutionService;

    /**
     * Envia convite para cadastro de administrador.
     */
    @Auditable(
        action = "CREATE",
        entity = "Admin"
    )
    public PendingRegistrationDTO createAdmin(
            CreateUserDTO dto
    ) {
        return invite(
            dto.name(),
            dto.email(),
            UserRole.ADMIN,
            null
        );
    }

    /**
     * Envia convite para cadastro de gestor.
     */
    @Auditable(
        action = "CREATE",
        entity = "Manager",
        institutionId = "#dto.institutionId()"
    )
    public PendingRegistrationDTO createManager(
            CreateInstitutionUserDTO dto
    ) {
        return invite(
            dto.name(),
            dto.email(),
            UserRole.MANAGER,
            dto.institutionId()
        );
    }

    /**
     * Envia convite para cadastro de professor.
     */
    @Auditable(
        action = "CREATE",
        entity = "Teacher",
        institutionId = "#dto.institutionId()"
    )
    public PendingRegistrationDTO createTeacher(
            CreateInstitutionUserDTO dto
    ) {
        return invite(
            dto.name(),
            dto.email(),
            UserRole.TEACHER,
            dto.institutionId()
        );
    }

    /**
     * Envia convite para cadastro de estudante.
     */
    @Auditable(
        action = "CREATE",
        entity = "Student",
        institutionId = "#dto.institutionId()"
    )
    public PendingRegistrationDTO createStudent(
            CreateInstitutionUserDTO dto
    ) {
        return invite(
            dto.name(),
            dto.email(),
            UserRole.STUDENT,
            dto.institutionId()
        );
    }

    /**
     * Completa o registro do usuário.
     */
    @Transactional
    @Auditable(
        action = "COMPLETE_REGISTRATION",
        entity = "User",
        entityId = "#result.id()",
        institutionId = "#result.institutionId()"
    )
    public UserDTO completeRegistration(
            CompleteRegistrationDTO dto
    ) {
        String email = UserUtils.normalizeEmail(
            dto.email()
        );

        termService.validateRequiredAccepted(
            dto.acceptedTerms()
        );

        PendingRegistration pending = pendingRepository
                .findByEmail(email)
                .orElseThrow(
                    () -> noPendingRegistrationFor(email)
                );

        if (
            pending.expiresAt().isBefore(
                Instant.now()
            )
        ) {
            pendingRepository.deleteByEmail(email);

            throw new ExpiredTwoFactorCodeException();
        }

        if (!pending.code().equals(dto.code())) {
            registerFailedAttempt(pending);

            throw new InvalidTwoFactorCodeException();
        }

        assertEmailAvailable(email);

        User user = newUserFrom(
            pending,
            passwordEncoder.encode(dto.password())
        );

        User saved;

        try {
            saved = userRepository.save(user);

        } catch (DataIntegrityViolationException ex) {
            if (isEmailDuplicate(ex)) {
                throw new EmailAlreadyInUseException();
            }

            throw ex;
        }

        termService.registerInitialAcceptances(
            saved,
            dto.acceptedTerms()
        );

        pendingRepository.deleteByEmail(email);

        return UserUtils.toDTO(saved);
    }

    /**
     * Envia um convite de cadastro.
     */
    private PendingRegistrationDTO invite(
            String name,
            String rawEmail,
            UserRole role,
            UUID institutionId
    ) {
        String email = UserUtils.normalizeEmail(
            rawEmail
        );

        assertEmailAvailable(email);

        PendingRegistration pending =
                new PendingRegistration(
                    name,
                    email,
                    role.getName(),
                    institutionId,
                    mfaService.generateCode(),
                    Instant.now().plus(
                        CodeType.INVITE.getDuration()
                    ),
                    0,
                    Instant.now()
                );

        // Reconvites substituem o cadastro pendente.
        pendingRepository.save(pending);

        notifierService.sendRegisterInvite(
            email,
            pending.code(),
            email
        );

        return new PendingRegistrationDTO(
            name,
            email,
            role,
            institutionId
        );
    }

    /**
     * Identifica a ausência de um registro pendente.
     */
    private RuntimeException noPendingRegistrationFor(
            String email
    ) {
        if (userRepository.existsByEmail(email)) {
            return new RegistrationAlreadyCompletedException();
        }

        return new UserNotFoundException();
    }

    /**
     * Identifica violações de unicidade de email.
     */
    private boolean isEmailDuplicate(
            DataIntegrityViolationException ex
    ) {
        if (
            ex.getMostSpecificCause()
                instanceof SQLIntegrityConstraintViolationException sqlEx
        ) {
            return sqlEx.getErrorCode() == 1062 ||
                   "23505".equals(sqlEx.getSQLState());
        }

        return false;
    }

    /**
     * Registra tentativas incorretas de confirmação.
     */
    private void registerFailedAttempt(
            PendingRegistration pending
    ) {
        int attempts = pending.attempts() + 1;

        if (attempts >= MAX_VERIFICATION_ATTEMPTS) {
            pendingRepository.deleteByEmail(
                pending.email()
            );

            return;
        }

        pendingRepository.savePreservingTtl(
            new PendingRegistration(
                pending.name(),
                pending.email(),
                pending.role(),
                pending.institutionId(),
                pending.code(),
                pending.expiresAt(),
                attempts,
                pending.createdAt()
            )
        );
    }

    /**
     * Constrói a entidade de acordo com o perfil.
     */
    private User newUserFrom(
            PendingRegistration pending,
            String encodedPassword
    ) {
        User user = switch (
            UserRole.valueOf(pending.role())
        ) {
            case ADMIN -> new Admin();
            case MANAGER -> new Manager();
            case TEACHER -> new Teacher();
            case STUDENT -> new Student();
        };

        user.setName(pending.name());
        user.setEmail(pending.email());
        user.setPassword(encodedPassword);

        InstitutionModel institutionModel = null;

        if (pending.institutionId() != null) {
            institutionModel = institutionService.findById(
                pending.institutionId()
            );
        }

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
     * Verifica disponibilidade do email.
     */
    public void assertEmailAvailable(
            String email
    ) {
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyInUseException();
        }
    }
}

