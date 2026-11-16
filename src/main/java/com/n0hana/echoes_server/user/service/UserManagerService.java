
package com.n0hana.echoes_server.user.service;

import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.n0hana.echoes_server.classroom.ClassroomMemberRepository;
import com.n0hana.echoes_server.infra.logs.Auditable;
import com.n0hana.echoes_server.institution.InstitutionModel;
import com.n0hana.echoes_server.institution.InstitutionService;
import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.UserUtils;

import com.n0hana.echoes_server.user.dto.UpdateInstitutionUserDTO;
import com.n0hana.echoes_server.user.dto.UpdateUserDTO;
import com.n0hana.echoes_server.user.dto.UserDTO;

import com.n0hana.echoes_server.user.exception.EmailAlreadyInUseException;
import com.n0hana.echoes_server.user.exception.InvalidUserTypeException;
import com.n0hana.echoes_server.user.exception.LastManagerException;
import com.n0hana.echoes_server.user.exception.UserNotFoundException;

import com.n0hana.echoes_server.user.model.Admin;
import com.n0hana.echoes_server.user.model.Manager;
import com.n0hana.echoes_server.user.model.Student;
import com.n0hana.echoes_server.user.model.Teacher;
import com.n0hana.echoes_server.user.model.User;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

/**
 * Serviço para gerenciar alterações nos usuários do sistema.
 *
 * @author Enrico Bertozzi
 */
@Service
@RequiredArgsConstructor
public class UserManagerService {

    private final UserRepository userRepository;
    private final InstitutionService institutionService;
    private final ClassroomMemberRepository classroomMemberRepository;

    /**
     * Atualiza um administrador.
     */
    @Transactional
    @Auditable(
        action = "UPDATE",
        entity = "Admin",
        entityId = "#id"
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
            dto.email()
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
            dto.email()
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
            dto.email()
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
            dto.email()
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
        softDelete(
            findTyped(id, Admin.class)
        );
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

        userRepository.save(user);

        classroomMemberRepository.deleteByUserId(
            user.getId()
        );
    }

    /**
     * Impede que a instituição fique sem gestor.
     */
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
            String email
    ) {
        if (name != null) {
            user.setName(name);
        }

        if (email != null) {
            String normalized =
                    UserUtils.normalizeEmail(email);

            if (
                !normalized.equals(
                    user.getEmail()
                )
            ) {
                assertEmailAvailable(normalized);
                user.setEmail(normalized);
            }
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

