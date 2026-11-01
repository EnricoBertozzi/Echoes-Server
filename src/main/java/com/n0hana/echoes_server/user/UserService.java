package com.n0hana.echoes_server.user;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.n0hana.echoes_server.infra.logs.Auditable;
import com.n0hana.echoes_server.infra.notifier.NotifierService;
import com.n0hana.echoes_server.institution.InstitutionModel;
import com.n0hana.echoes_server.mfa.TwoFactorDTO;
import com.n0hana.echoes_server.mfa.TwoFactorService;
import com.n0hana.echoes_server.term.TermService;
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
import com.n0hana.echoes_server.user.model.Admin;
import com.n0hana.echoes_server.user.model.Manager;
import com.n0hana.echoes_server.user.model.Student;
import com.n0hana.echoes_server.user.model.Teacher;
import com.n0hana.echoes_server.user.model.User;
import com.n0hana.echoes_server.user.model.UserRole;

import org.springframework.dao.DataIntegrityViolationException;
import java.sql.SQLIntegrityConstraintViolationException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PendingRegistrationRepository pendingRegistrationRepository;

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
    @Auditable(action = "UPDATE", entity = "Admin")
    public UserDTO updateAdmin(UUID id, UpdateUserDTO dto) {
        Admin admin = findTyped(id, Admin.class);
//        applyCommonUpdate(admin, dto.name(), dto.email());
        return toDTO(userRepository.save(admin));
    }

    @Transactional
    @Auditable(action = "UPDATE", entity = "Manager")
    public UserDTO updateManager(UUID id, UpdateInstitutionUserDTO dto) {
        Manager manager = findTyped(id, Manager.class);
//        applyCommonUpdate(manager, dto.name(), dto.email());
        if (dto.institutionId() != null) {
            // manager.setInstitutionId(dto.institutionId());
        }
        return toDTO(userRepository.save(manager));
    }

    @Transactional
    @Auditable(action = "UPDATE", entity = "Teacher")
    public UserDTO updateTeacher(UUID id, UpdateInstitutionUserDTO dto) {
        Teacher teacher = findTyped(id, Teacher.class);
//        applyCommonUpdate(teacher, dto.name(), dto.email());
        if (dto.institutionId() != null) {
            //teacher.setInstitutionId(dto.institutionId());
        }
        return toDTO(userRepository.save(teacher));
    }

    @Transactional
    @Auditable(action = "UPDATE", entity = "Student")
    public UserDTO updateStudent(UUID id, UpdateInstitutionUserDTO dto) {
        Student student = findTyped(id, Student.class);
//        applyCommonUpdate(student, dto.name(), dto.email());
        if (dto.institutionId() != null) {
            //student.setInstitutionId(dto.institutionId());
        }
        return toDTO(userRepository.save(student));
    }

    @Transactional
    @Auditable(action = "DELETE", entity = "Admin")
    public void deleteAdmin(UUID id) {
        softDelete(findTyped(id, Admin.class));
    }

    @Transactional
    @Auditable(action = "DELETE", entity = "Manager")
    public void deleteManager(UUID id) {
        softDelete(findTyped(id, Manager.class));
    }

    @Transactional
    @Auditable(action = "DELETE", entity = "Teacher")
    public void deleteTeacher(UUID id) {
        softDelete(findTyped(id, Teacher.class));
    }

    @Transactional
    @Auditable(action = "DELETE", entity = "Student")
    public void deleteStudent(UUID id) {
        softDelete(findTyped(id, Student.class));
    }

//    private void applyCommonUpdate(User user, String name, String email) {
//        if (name != null) {
//            user.setName(name);
//        }
//        if (email != null) {
//            String normalized = normalizeEmail(email);
//            if (!normalized.equals(user.getEmail())) {
//                assertEmailAvailable(normalized);
//                user.setEmail(normalized);
//            }
//        }
//    }

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

    private UserDTO toDTO(User user) {
        InstitutionModel institutionModel = institutionIdOf(user);
        return new UserDTO(
                user.getId(),
                user.getName(),
                user.getEmail(),
                institutionModel.getId(),
                user.getUserRole());
    }

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
