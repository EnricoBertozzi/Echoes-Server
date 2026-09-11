package com.n0hana.echoes_server.user;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.n0hana.echoes_server.mfa.TwoFactorDTO;
import com.n0hana.echoes_server.mfa.TwoFactorService;
import com.n0hana.echoes_server.notifier.TwoFactorNotifier;
import com.n0hana.echoes_server.user.dto.CompleteRegistrationDTO;
import com.n0hana.echoes_server.user.dto.CreateInstitutionUserDTO;
import com.n0hana.echoes_server.user.dto.CreateUserDTO;
import com.n0hana.echoes_server.user.dto.PendingRegistrationDTO;
import com.n0hana.echoes_server.user.dto.UpdateInstitutionUserDTO;
import com.n0hana.echoes_server.user.dto.UpdateUserDTO;
import com.n0hana.echoes_server.user.dto.UserDTO;
import com.n0hana.echoes_server.user.exception.EmailAlreadyInUseException;
import com.n0hana.echoes_server.user.exception.ExpiredTwoFactorCodeException;
import com.n0hana.echoes_server.user.exception.InvalidTwoFactorCodeException;
import com.n0hana.echoes_server.user.exception.InvalidUserTypeException;
import com.n0hana.echoes_server.user.exception.RegistrationAlreadyCompletedException;
import com.n0hana.echoes_server.user.exception.UserNotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    static final int MAX_VERIFICATION_ATTEMPTS = 5;

    private final UserRepository userRepository;
    private final TwoFactorService twoFactorService;
    private final PendingRegistrationRepository pendingRegistrationRepository;
    private final TwoFactorNotifier notifier;
    private final PasswordEncoder passwordEncoder;

    public PendingRegistrationDTO createAdmin(CreateUserDTO dto) {
        return invite(dto.name(), dto.email(), UserRole.ADMIN, null);
    }

    public PendingRegistrationDTO createManager(CreateInstitutionUserDTO dto) {
        return invite(dto.name(), dto.email(), UserRole.MANAGER, dto.institutionId());
    }

    public PendingRegistrationDTO createTeacher(CreateInstitutionUserDTO dto) {
        return invite(dto.name(), dto.email(), UserRole.TEACHER, dto.institutionId());
    }

    public PendingRegistrationDTO createStudent(CreateInstitutionUserDTO dto) {
        return invite(dto.name(), dto.email(), UserRole.STUDENT, dto.institutionId());
    }

    public UserDTO findAdminById(UUID id) {
        return toDTO(findTyped(id, Admin.class));
    }

    public UserDTO findManagerById(UUID id) {
        return toDTO(findTyped(id, Manager.class));
    }

    public UserDTO findTeacherById(UUID id) {
        return toDTO(findTyped(id, Teacher.class));
    }

    public UserDTO findStudentById(UUID id) {
        return toDTO(findTyped(id, Student.class));
    }

    public Page<UserDTO> findAdmins(Pageable pageable) {
        return userRepository.findAdmins(pageable).map(this::toDTO);
    }

    public Page<UserDTO> findManagers(UUID institutionId, Pageable pageable) {
        return userRepository.findManagers(institutionId, pageable).map(this::toDTO);
    }

    public Page<UserDTO> findTeachers(UUID institutionId, Pageable pageable) {
        return userRepository.findTeachers(institutionId, pageable).map(this::toDTO);
    }

    public Page<UserDTO> findStudents(UUID institutionId, Pageable pageable) {
        return userRepository.findStudents(institutionId, pageable).map(this::toDTO);
    }

    @Transactional
    public UserDTO updateAdmin(UUID id, UpdateUserDTO dto) {
        Admin admin = findTyped(id, Admin.class);
        applyCommonUpdate(admin, dto.name(), dto.email());
        return toDTO(userRepository.save(admin));
    }

    @Transactional
    public UserDTO updateManager(UUID id, UpdateInstitutionUserDTO dto) {
        Manager manager = findTyped(id, Manager.class);
        applyCommonUpdate(manager, dto.name(), dto.email());
        if (dto.institutionId() != null) {
            manager.setInstitutionId(dto.institutionId());
        }
        return toDTO(userRepository.save(manager));
    }

    @Transactional
    public UserDTO updateTeacher(UUID id, UpdateInstitutionUserDTO dto) {
        Teacher teacher = findTyped(id, Teacher.class);
        applyCommonUpdate(teacher, dto.name(), dto.email());
        if (dto.institutionId() != null) {
            teacher.setInstitutionId(dto.institutionId());
        }
        return toDTO(userRepository.save(teacher));
    }

    @Transactional
    public UserDTO updateStudent(UUID id, UpdateInstitutionUserDTO dto) {
        Student student = findTyped(id, Student.class);
        applyCommonUpdate(student, dto.name(), dto.email());
        if (dto.institutionId() != null) {
            student.setInstitutionId(dto.institutionId());
        }
        return toDTO(userRepository.save(student));
    }

    @Transactional
    public void deleteAdmin(UUID id) {
        softDelete(findTyped(id, Admin.class));
    }

    @Transactional
    public void deleteManager(UUID id) {
        softDelete(findTyped(id, Manager.class));
    }

    @Transactional
    public void deleteTeacher(UUID id) {
        softDelete(findTyped(id, Teacher.class));
    }

    @Transactional
    public void deleteStudent(UUID id) {
        softDelete(findTyped(id, Student.class));
    }

    @Transactional
    public UserDTO completeRegistration(CompleteRegistrationDTO dto) {
        String email = normalizeEmail(dto.email());

        PendingRegistration pending = pendingRegistrationRepository.findByEmail(email)
                .orElseThrow(() -> noPendingRegistrationFor(email));

        if (!pending.code().equals(dto.code())) {
            registerFailedAttempt(pending);
            throw new InvalidTwoFactorCodeException();
        }

        if (pending.expiresAt().isBefore(Instant.now())) {
            throw new ExpiredTwoFactorCodeException();
        }

        assertEmailAvailable(email);

        User user = newUserFrom(pending, passwordEncoder.encode(dto.password()));
        User saved = userRepository.save(user);

        // Se o commit falhar após o delete, um novo POST reconvita (reenvio)
        // e o TTL do Redis limpa resíduos sozinho: o fluxo se auto-cura.
        pendingRegistrationRepository.deleteByEmail(email);

        return toDTO(saved);
    }

    private PendingRegistrationDTO invite(String name, String rawEmail, UserRole role, UUID institutionId) {
        String email = normalizeEmail(rawEmail);
        assertEmailAvailable(email);

        PendingRegistration pending = new PendingRegistration(
                name,
                email,
                role.getName(),
                institutionId,
                twoFactorService.generateCode(),
                Instant.now().plusSeconds(300),
                0,
                Instant.now());

        // Reconvites de um mesmo e-mail pendente sobrescrevem o registro:
        // este é o caminho de reenvio para código perdido ou expirado.
        pendingRegistrationRepository.save(pending);

        // notifier.send é síncrono e SEGURO porque EmailNotifier/LoggerNotifier
        // engolem todas as exceções (verificado): uma falha externa de e-mail
        // não deixa nada inconsistente — o convite expira via TTL e um novo
        // POST reenvia o código.
        notifier.send(new TwoFactorDTO(email, pending.code(), pending.expiresAt()));

        return new PendingRegistrationDTO(name, email, role, institutionId);
    }

    private RuntimeException noPendingRegistrationFor(String email) {
        if (userRepository.existsByEmail(email)) {
            return new RegistrationAlreadyCompletedException();
        }
        return new UserNotFoundException();
    }

    private void registerFailedAttempt(PendingRegistration pending) {
        int attempts = pending.attempts() + 1;
        if (attempts >= MAX_VERIFICATION_ATTEMPTS) {
            // Esgotou as tentativas: invalida o convite inteiro para impedir
            // força bruta do código de 6 dígitos; um novo POST reenvia.
            pendingRegistrationRepository.deleteByEmail(pending.email());
            return;
        }
        pendingRegistrationRepository.savePreservingTtl(new PendingRegistration(
                pending.name(),
                pending.email(),
                pending.role(),
                pending.institutionId(),
                pending.code(),
                pending.expiresAt(),
                attempts,
                pending.createdAt()));
    }

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
        if (user instanceof Manager manager) {
            manager.setInstitutionId(pending.institutionId());
        } else if (user instanceof Teacher teacher) {
            teacher.setInstitutionId(pending.institutionId());
        } else if (user instanceof Student student) {
            student.setInstitutionId(pending.institutionId());
        }
        return user;
    }

    private void assertEmailAvailable(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyInUseException();
        }
    }

    private void applyCommonUpdate(User user, String name, String email) {
        if (name != null) {
            user.setName(name);
        }
        if (email != null) {
            String normalized = normalizeEmail(email);
            if (!normalized.equals(user.getEmail())) {
                assertEmailAvailable(normalized);
                user.setEmail(normalized);
            }
        }
    }

    private void softDelete(User user) {
        user.setActive(false);
        userRepository.save(user);
    }

    private <T extends User> T findTyped(UUID id, Class<T> type) {
        User user = userRepository.findById(id)
                .filter(u -> u.isActive())
                .orElseThrow(UserNotFoundException::new);

        if (!type.isInstance(user)) {
            throw new InvalidUserTypeException();
        }

        return type.cast(user);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private UserDTO toDTO(User user) {
        return new UserDTO(
                user.getId(),
                user.getName(),
                user.getEmail(),
                institutionIdOf(user),
                user.getUserRole()
        );
    }

    private UUID institutionIdOf(User user) {
        if (user instanceof Manager manager) {
            return manager.getInstitutionId();
        }
        if (user instanceof Teacher teacher) {
            return teacher.getInstitutionId();
        }
        if (user instanceof Student student) {
            return student.getInstitutionId();
        }
        return null;
    }
}
