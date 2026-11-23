package com.n0hana.echoes_server.user.service;

import com.n0hana.echoes_server.infra.web.PageLimits;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.n0hana.echoes_server.infra.logs.Auditable;
import com.n0hana.echoes_server.institution.InstitutionModel;
import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.UserUtils;
import com.n0hana.echoes_server.user.dto.UserEmailDTO;
import com.n0hana.echoes_server.user.dto.UserDTO;
import com.n0hana.echoes_server.user.exception.InvalidUserTypeException;
import com.n0hana.echoes_server.user.exception.UserNotFoundException;
import com.n0hana.echoes_server.user.model.Admin;
import com.n0hana.echoes_server.user.model.Manager;
import com.n0hana.echoes_server.user.model.Student;
import com.n0hana.echoes_server.user.model.Teacher;
import com.n0hana.echoes_server.user.model.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserInfoService {

    private final UserRepository userRepository;

    public UserDTO findAdminById(UUID id) {
        return UserUtils.toDTO(findTyped(id, Admin.class));
    }

    public UserDTO findManagerById(UUID id) {
        return UserUtils.toDTO(findTyped(id, Manager.class));
    }

    public UserDTO findTeacherById(UUID id) {
        return UserUtils.toDTO(findTyped(id, Teacher.class));
    }

    public UserDTO findStudentById(UUID id) {
        return UserUtils.toDTO(findTyped(id, Student.class));
    }

    public Page<UserDTO> findAdmins(Pageable pageable) {
        return userRepository.findAdmins(pageable).map(UserUtils::toMaskedDTO);
    }

    public Page<UserDTO> findManagers(UUID institutionId, Pageable pageable) {
        return userRepository.findManagers(institutionId, pageable).map(UserUtils::toMaskedDTO);
    }

    public Page<UserDTO> findTeachers(UUID institutionId, Pageable pageable) {
        return userRepository.findTeachers(institutionId, pageable).map(UserUtils::toMaskedDTO);
    }

    public Page<UserDTO> findStudents(UUID institutionId, Pageable pageable) {
        return userRepository.findStudents(institutionId, pageable).map(UserUtils::toMaskedDTO);
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

    public Page<UserDTO> findAdminByName(String name, Pageable pageable) {
        return userRepository.findAdminsByName(name, pageable).map(UserUtils::toMaskedDTO);
    }

    /**
     * Busca usuários por e-mail no banco de dados
     * 
     * @param email E-mail do usuário a ser procurado
     * 
     * @throws UserNotFoundException caso o e-mail não esteja cadastrado
     * 
     * @return {@link UserDTO} com dados do usuário
     *         encontrado
     */
    public UserDTO findUserByEmail(String email) {
        User user = userRepository.findByEmail(email).orElseThrow(UserNotFoundException::new);
        return UserUtils.toDTO(user);
    }

    /**
     * Busca os usuários membros de uma instituição
     * 
     * @param id   Identificador da instituição
     * @param page Index de paginação
     * @param size Quantidade de elementos da paginação
     * 
     * @return {@link Page} com dados dos usuários membros
     */
    public Page<UserDTO> findUsersByInstitution(UUID id, Integer page, Integer size) {
        Pageable pageable = PageRequest.of(PageLimits.clampPage(page), PageLimits.clamp(size));
        return userRepository.findUsersByInstitutionId(id, pageable).map(UserUtils::toMaskedDTO);
    }

    /**
     * Busca os usuários por nome dentro de uma instituição
     * 
     * @param id   Identificador da instituição
     * @param name Nome do usuário
     * @param page Index de páginação
     * @param size Quantidade de elementos da paginação
     * @return {@link Page} com usuários que atendam a busca
     */
    public Page<UserDTO> findUsersByNameInInstitution(UUID id, String name, Integer page, Integer size) {
        Pageable pageable = PageRequest.of(PageLimits.clampPage(page), PageLimits.clamp(size));
        return userRepository.findUsersByNameInInstitution(id, name, pageable).map(UserUtils::toMaskedDTO);
    }

    /**
     * Retorna o e-mail completo de um usuário e registra a visualização.
     *
     * @param id Identificador do usuário
     * @return {@link UserEmailDTO} com o e-mail completo do usuário
     */
    @Auditable(
        action = "VIEW_EMAIL",
        entity = "User",
        entityId = "#id",
        entityName = "#result.name()",
        institutionId = "#result.institutionId()",
        details = "'E-mail visualizado'"
    )
    public UserEmailDTO findEmail(UUID id) {
        User user = userRepository.findById(id)
                .filter(u -> u.isActive())
                .orElseThrow(UserNotFoundException::new);

        InstitutionModel institution = UserUtils.institutionOf(user);

        return new UserEmailDTO(
                user.getId(),
                user.getName(),
                user.getEmail(),
                institution == null ? null : institution.getId());
    }
}
