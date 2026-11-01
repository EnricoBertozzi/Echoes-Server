package com.n0hana.echoes_server.user.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

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
import com.n0hana.echoes_server.user.exception.UserNotFoundException;
import com.n0hana.echoes_server.user.model.Admin;
import com.n0hana.echoes_server.user.model.Manager;
import com.n0hana.echoes_server.user.model.Student;
import com.n0hana.echoes_server.user.model.Teacher;
import com.n0hana.echoes_server.user.model.User;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

/**
 * Serviço para gerenciar alterações nos usuários do sistema
 * 
 * @author Enrico Bertozzi
 */
@Service
@RequiredArgsConstructor
public class UserManagerService {

    private final UserRepository userRepository;

    private final InstitutionService institutionService;

    /**
     * Método para alteração de usuários administradores
     * 
     * @param id  Identificador do usuário
     * @param dto Objeto com dados para alteração
     * @return Objeto com os dados alterados do usuário
     */
    @Transactional
    @Auditable(action = "UPDATE", entity = "Admin")
    public UserDTO updateAdmin(UUID id, UpdateUserDTO dto) {
        Admin admin = findTyped(id, Admin.class);
        applyCommonUpdate(admin, dto.name(), dto.email());
        return UserUtils.toDTO(userRepository.save(admin));
    }

    /**
     * Método para alteração de usuários gestores
     * 
     * @param id  Identificador do usuário
     * @param dto Objeto com dados para alteração
     * @return Objeto com os dados alterados do usuário
     */
    @Transactional
    @Auditable(action = "UPDATE", entity = "Manager")
    public UserDTO updateManager(UUID id, UpdateInstitutionUserDTO dto) {
        Manager manager = findTyped(id, Manager.class);
        applyCommonUpdate(manager, dto.name(), dto.email());
        if (dto.institutionId() != null) {
            InstitutionModel institutionModel = institutionService.findById(dto.institutionId());
            manager.setInstitution(institutionModel);
        }
        return UserUtils.toDTO(userRepository.save(manager));
    }

    /**
     * Método para alteração de usuários professores
     * 
     * @param id  Identificador do usuário
     * @param dto Objeto com dados para alteração
     * @return Objeto com os dados alterados do usuário
     */
    @Transactional
    @Auditable(action = "UPDATE", entity = "Teacher")
    public UserDTO updateTeacher(UUID id, UpdateInstitutionUserDTO dto) {
        Teacher teacher = findTyped(id, Teacher.class);
        applyCommonUpdate(teacher, dto.name(), dto.email());
        if (dto.institutionId() != null) {
            InstitutionModel institutionModel = institutionService.findById(dto.institutionId());
            teacher.setInstitution(institutionModel);
        }
        return UserUtils.toDTO(userRepository.save(teacher));
    }

    /**
     * Método para alteração de usuários alunos
     * 
     * @param id  Identificador do usuário
     * @param dto Objeto com dados para alteração
     * @return Objeto com os dados alterados do usuário
     */
    @Transactional
    @Auditable(action = "UPDATE", entity = "Student")
    public UserDTO updateStudent(UUID id, UpdateInstitutionUserDTO dto) {
        Student student = findTyped(id, Student.class);
        applyCommonUpdate(student, dto.name(), dto.email());
        if (dto.institutionId() != null) {
            InstitutionModel institutionModel = institutionService.findById(dto.institutionId());
            student.setInstitution(institutionModel);
        }
        return UserUtils.toDTO(userRepository.save(student));
    }

    /**
     * Método para exclusão lógica dos usuários administradores
     * 
     * @param id Identificador do usuário
     */
    @Transactional
    @Auditable(action = "DELETE", entity = "Admin")
    public void deleteAdmin(UUID id) {
        softDelete(findTyped(id, Admin.class));
    }

    /**
     * Método para exclusão lógica dos usuários gerentes
     * 
     * @param id Identificador do usuário
     */
    @Transactional
    @Auditable(action = "DELETE", entity = "Manager")
    public void deleteManager(UUID id) {
        softDelete(findTyped(id, Manager.class));
    }

    /**
     * Método para exclusão lógica dos usuários professores
     * 
     * @param id Identificador do usuário
     */
    @Transactional
    @Auditable(action = "DELETE", entity = "Teacher")
    public void deleteTeacher(UUID id) {
        softDelete(findTyped(id, Teacher.class));
    }

    /**
     * Método para exclusão lógica dos usuários alunos
     * 
     * @param id Identificador do usuário
     */
    @Transactional
    @Auditable(action = "DELETE", entity = "Student")
    public void deleteStudent(UUID id) {
        softDelete(findTyped(id, Student.class));
    }

    /**
     * Método para exclusão lógica dos usuários
     * 
     * @param id Identificador do usuário
     */
    private void softDelete(User user) {
        user.setActive(false);
        userRepository.save(user);
    }

    /**
     * Método para conversão do tipo de usuário da superclasse para subclasses
     * 
     * @param <T>  Tipo de do usuário
     * @param id   Identificador do usuário
     * @param type Tipo do usuário
     * @return O usuário especificado pelo tipo
     */
    private <T extends User> T findTyped(UUID id, Class<T> type) {
        User user = userRepository.findById(id)
                .filter(u -> u.isActive())
                .orElseThrow(UserNotFoundException::new);

        if (!type.isInstance(user)) {
            throw new InvalidUserTypeException();
        }

        return type.cast(user);
    }

    /**
     * Atualiza os dados do usuário
     * 
     * @param user  Usuário a ser alterado
     * @param name  Alteação no nome
     * @param email Alteração no e-mail
     */
    private void applyCommonUpdate(User user, String name, String email) {
        if (name != null) {
            user.setName(name);
        }
        if (email != null) {
            String normalized = UserUtils.normalizeEmail(email);
            if (!normalized.equals(user.getEmail())) {
                assertEmailAvailable(normalized);
                user.setEmail(normalized);
            }
        }
    }

    /**
     * Verifica se o e-mail está disponível
     * 
     * @param email e-mail a ser verificado
     */
    public void assertEmailAvailable(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyInUseException();
        }
    }

}
