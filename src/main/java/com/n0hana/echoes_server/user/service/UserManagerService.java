
package com.n0hana.echoes_server.user.service;

import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.n0hana.echoes_server.classroom.ClassroomMemberRepository;
import com.n0hana.echoes_server.infra.logs.Auditable;
import com.n0hana.echoes_server.auth.device.KnownDeviceService;
import com.n0hana.echoes_server.infra.javers.anonymization.JaversAnonymizationService;
import com.n0hana.echoes_server.institution.InstitutionModel;
import com.n0hana.echoes_server.institution.InstitutionService;
import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.UserUtils;

import com.n0hana.echoes_server.user.dto.UpdateInstitutionUserDTO;
import com.n0hana.echoes_server.user.dto.UpdateUserDTO;
import com.n0hana.echoes_server.user.dto.UserDTO;

import com.n0hana.echoes_server.user.exception.EmailAlreadyInUseException;
import com.n0hana.echoes_server.user.exception.InvalidUserTypeException;
import com.n0hana.echoes_server.user.exception.LastAdminException;
import com.n0hana.echoes_server.user.exception.LastManagerException;
import com.n0hana.echoes_server.user.exception.UserNotFoundException;

import com.n0hana.echoes_server.user.model.Admin;
import com.n0hana.echoes_server.user.model.Manager;
import com.n0hana.echoes_server.user.model.Student;
import com.n0hana.echoes_server.user.model.Teacher;
import com.n0hana.echoes_server.user.model.User;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço para gerenciar alterações nos usuários do sistema.
 *
 * @author Enrico Bertozzi
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserManagerService {

    private final UserRepository userRepository;
    private final InstitutionService institutionService;
    private final ClassroomMemberRepository classroomMemberRepository;
    private final JaversAnonymizationService javersAnonymization;
    private final KnownDeviceService knownDevices;

    /**
     * Atualiza um administrador.
     */
    @Transactional
    @Auditable(
        action = "UPDATE",
        entity = "Admin",
        entityId = "#id",
        entityName = "#result.name()"
    )
    public UserDTO updateAdmin(
            UUID id,
            UpdateUserDTO dto
    ) {
        Admin admin = findTyped(
            id,
            Admin.class
        );

        applyCommonUpdate(
            admin,
            dto.name(),
            dto.surname()
        );

        return UserUtils.toDTO(
            userRepository.save(admin)
        );
    }

    /**
     * Atualiza um gestor.
     */
    @Transactional
    @Auditable(
        action = "UPDATE",
        entity = "Manager",
        entityId = "#id",
        entityName = "#result.name()",
        institutionId = "#result.institutionId()"
    )
    public UserDTO updateManager(
            UUID id,
            UpdateInstitutionUserDTO dto
    ) {
        Manager manager = findTyped(
            id,
            Manager.class
        );

        applyCommonUpdate(
            manager,
            dto.name(),
            dto.surname()
        );

        if (dto.institutionId() != null) {
            InstitutionModel institutionModel =
                    institutionService.findById(
                        dto.institutionId()
                    );

            // O último gestor não pode ser transferido.
            if (
                manager.getInstitution() != null &&
                !Objects.equals(
                    manager.getInstitution().getId(),
                    institutionModel.getId()
                )
            ) {
                assertNotLastManager(manager);
            }

            manager.setInstitution(
                institutionModel
            );
        }

        return UserUtils.toDTO(
            userRepository.save(manager)
        );
    }

    /**
     * Atualiza um professor.
     */
    @Transactional
    @Auditable(
        action = "UPDATE",
        entity = "Teacher",
        entityId = "#id",
        entityName = "#result.name()",
        institutionId = "#result.institutionId()"
    )
    public UserDTO updateTeacher(
            UUID id,
            UpdateInstitutionUserDTO dto
    ) {
        Teacher teacher = findTyped(
            id,
            Teacher.class
        );

        applyCommonUpdate(
            teacher,
            dto.name(),
            dto.surname()
        );

        if (dto.institutionId() != null) {
            InstitutionModel institutionModel =
                    institutionService.findById(
                        dto.institutionId()
                    );

            teacher.setInstitution(
                institutionModel
            );
        }

        return UserUtils.toDTO(
            userRepository.save(teacher)
        );
    }

    /**
     * Atualiza um estudante.
     */
    @Transactional
    @Auditable(
        action = "UPDATE",
        entity = "Student",
        entityId = "#id",
        entityName = "#result.name()",
        institutionId = "#result.institutionId()"
    )
    public UserDTO updateStudent(
            UUID id,
            UpdateInstitutionUserDTO dto
    ) {
        Student student = findTyped(
            id,
            Student.class
        );

        applyCommonUpdate(
            student,
            dto.name(),
            dto.surname()
        );

        if (dto.institutionId() != null) {
            InstitutionModel institutionModel =
                    institutionService.findById(
                        dto.institutionId()
                    );

            student.setInstitution(
                institutionModel
            );
        }

        return UserUtils.toDTO(
            userRepository.save(student)
        );
    }

    @Transactional
    @Auditable(
        action = "UPDATE",
        entity = "User",
        entityId = "#result.id()"
    )
    public UserDTO updateCurrentUser(
            String email,
            UpdateUserDTO dto
    ) {
        User user = userRepository
                .findByEmail(email)
                .orElseThrow(UserNotFoundException::new);

        applyCommonUpdate(
                user,
                dto.name(),
            dto.surname()
        );

        return UserUtils.toDTO(
                userRepository.save(user)
        );
    }

    @Transactional
    @Auditable(
        action = "DELETE",
        entity = "User",
        entityId = "#result"
    )
    public UUID deleteCurrentUser(
            String email
    ) {
        User user = userRepository
                .findByEmail(email)
                .orElseThrow(UserNotFoundException::new);

        if (user instanceof Manager manager) {
            assertNotLastManager(manager);
        }

        if (user instanceof Admin admin) {
            assertNotLastAdmin(admin);
        }

        UUID id = user.getId();
        softDelete(user);
        return id;
    }

    /**
     * Exclusão lógica de administrador.
     */
    @Transactional
    @Auditable(
        action = "DELETE",
        entity = "Admin",
        entityId = "#id"
    )
    public void deleteAdmin(UUID id) {
        Admin admin = findTyped(id, Admin.class);
        assertNotLastAdmin(admin);

        softDelete(admin);
    }

    /**
     * Exclusão lógica de gestor.
     */
    @Transactional
    @Auditable(
        action = "DELETE",
        entity = "Manager",
        entityId = "#id"
    )
    public void deleteManager(UUID id) {
        Manager manager = findTyped(
            id,
            Manager.class
        );

        assertNotLastManager(manager);

        softDelete(manager);
    }

    /**
     * Exclusão lógica de professor.
     */
    @Transactional
    @Auditable(
        action = "DELETE",
        entity = "Teacher",
        entityId = "#id"
    )
    public void deleteTeacher(UUID id) {
        softDelete(
            findTyped(id, Teacher.class)
        );
    }

    /**
     * Exclusão lógica de estudante.
     */
    @Transactional
    @Auditable(
        action = "DELETE",
        entity = "Student",
        entityId = "#id"
    )
    public void deleteStudent(UUID id) {
        softDelete(
            findTyped(id, Student.class)
        );
    }

    /**
     * Exclusão lógica e liberação de vínculos com turmas.
     */
    private void softDelete(User user) {
        user.setActive(false);
        anonymize(user);

        userRepository.save(user);

        classroomMemberRepository.deleteByUserId(
            user.getId()
        );

        UUID userId = user.getId();
        afterCommit(() -> {
            javersAnonymization.anonymizeAfterSoftDelete(userId);
            try {
                knownDevices.forget(userId);
            } catch (RuntimeException e) {
                log.warn("Não foi possível apagar os navegadores conhecidos do usuário {}: {}",
                        userId, e.getClass().getSimpleName());
            }
        });
    }

    /**
     * Executa a limpeza fora do banco (histórico do Javers, Redis) só depois que a
     * exclusão foi confirmada: num rollback, a conta continua intacta.
     */
    private static void afterCommit(Runnable cleanup) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cleanup.run();
                }
            });
        } else {
            cleanup.run();
        }
    }

    private void anonymize(User user) {
        user.setName(null);
        user.setEmail("deleted_user_" + UUID.randomUUID() + "@deleted.local");
        user.setPassword(null);
        user.setLoginAttempts(0);
        user.setLockUntil(null);

        if (user instanceof Manager manager) {
            manager.setInstitution(null);
        } else if (user instanceof Teacher teacher) {
            teacher.setInstitution(null);
        } else if (user instanceof Student student) {
            student.setInstitution(null);
        }
    }

    /**
     * Impede que a instituição fique sem gestor.
     */
    private void assertNotLastAdmin(Admin admin) {
        if (admin.isActive() && userRepository.countActiveAdmins() <= 1)
            throw new LastAdminException();
    }

    private void assertNotLastManager(
            Manager manager
    ) {
        InstitutionModel institution =
                manager.getInstitution();

        if (
            institution != null &&
            userRepository.countActiveManagers(
                institution.getId()
            ) <= 1
        ) {
            throw new LastManagerException();
        }
    }

    /**
     * Busca usuário ativo e verifica seu subtipo.
     */
    private <T extends User> T findTyped(
            UUID id,
            Class<T> type
    ) {
        User user = userRepository
                .findById(id)
                .filter(User::isActive)
                .orElseThrow(
                    UserNotFoundException::new
                );

        if (!type.isInstance(user)) {
            throw new InvalidUserTypeException();
        }

        return type.cast(user);
    }

    /**
     * Atualiza dados comuns do usuário.
     */
    private void applyCommonUpdate(
            User user,
            String name,
            String surname
    ) {
        if (name != null) {
            user.setName(name);
        }
        if (surname != null) {
            user.setSurname(surname);
        }
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

