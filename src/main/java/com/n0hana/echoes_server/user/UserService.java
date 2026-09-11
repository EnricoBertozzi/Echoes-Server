package com.n0hana.echoes_server.user;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.n0hana.echoes_server.mfa.InMemoryTwoFactorRepository;
import com.n0hana.echoes_server.mfa.TwoFactorDTO;
import com.n0hana.echoes_server.mfa.TwoFactorService;
import com.n0hana.echoes_server.notifier.TwoFactorNotifier;
import com.n0hana.echoes_server.user.dto.CompleteRegistrationDTO;
import com.n0hana.echoes_server.user.dto.CreateInstitutionUserDTO;
import com.n0hana.echoes_server.user.dto.CreateUserDTO;
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

    private final UserRepository userRepository;
    private final TwoFactorService twoFactorService;
    private final InMemoryTwoFactorRepository twoFactorRepository;
    private final TwoFactorNotifier notifier;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserDTO createAdmin(CreateUserDTO dto) {
        String email = normalizeEmail(dto.email());
        assertEmailAvailable(email);

        Admin admin = new Admin();
        admin.setName(dto.name());
        admin.setEmail(email);

        Admin saved = userRepository.save(admin);
        sendRegistrationCode(email);
        return toDTO(saved);
    }

    @Transactional
    public UserDTO createManager(CreateInstitutionUserDTO dto) {
        String email = normalizeEmail(dto.email());
        assertEmailAvailable(email);

        Manager manager = new Manager();
        manager.setName(dto.name());
        manager.setEmail(email);
        manager.setInstitutionId(dto.institutionId());

        Manager saved = userRepository.save(manager);
        sendRegistrationCode(email);
        return toDTO(saved);
    }

    @Transactional
    public UserDTO createTeacher(CreateInstitutionUserDTO dto) {
        String email = normalizeEmail(dto.email());
        assertEmailAvailable(email);

        Teacher teacher = new Teacher();
        teacher.setName(dto.name());
        teacher.setEmail(email);
        teacher.setInstitutionId(dto.institutionId());

        Teacher saved = userRepository.save(teacher);
        sendRegistrationCode(email);
        return toDTO(saved);
    }

    @Transactional
    public UserDTO createStudent(CreateInstitutionUserDTO dto) {
        String email = normalizeEmail(dto.email());
        assertEmailAvailable(email);

        Student student = new Student();
        student.setName(dto.name());
        student.setEmail(email);
        student.setInstitutionId(dto.institutionId());

        Student saved = userRepository.save(student);
        sendRegistrationCode(email);
        return toDTO(saved);
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

        User user = userRepository.findUserByEmail(email)
                .orElseThrow(UserNotFoundException::new);

        TwoFactorDTO token = twoFactorRepository.findByEmail(email)
                .orElseThrow(InvalidTwoFactorCodeException::new);

        if (!token.code().equals(dto.code())) {
            throw new InvalidTwoFactorCodeException();
        }

        if (token.expiresAt().isBefore(Instant.now())) {
            throw new ExpiredTwoFactorCodeException();
        }

        if (user.isRegistrationCompleted()) {
            throw new RegistrationAlreadyCompletedException();
        }

        user.setPassword(passwordEncoder.encode(dto.password()));
        user.setRegistrationCompleted(true);
        userRepository.save(user);

        twoFactorRepository.deleteByEmail(email);

        return toDTO(user);
    }

    private void sendRegistrationCode(String email) {
        String code = twoFactorService.generateCode();

        TwoFactorDTO token = new TwoFactorDTO(
                email,
                code,
                Instant.now().plusSeconds(300)
        );

        twoFactorRepository.save(token);

        // notifier.send é síncrono dentro da transação e SEGURO porque
        // EmailNotifier/LoggerNotifier engolem todas as exceções (verificado):
        // uma falha externa de e-mail não pode deixar o banco inconsistente.
        notifier.send(token);
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
