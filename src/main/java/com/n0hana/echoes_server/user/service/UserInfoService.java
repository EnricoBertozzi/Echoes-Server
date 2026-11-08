package com.n0hana.echoes_server.user.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.UserUtils;
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
        return userRepository.findAdmins(pageable).map(UserUtils::toDTO);
    }

    public Page<UserDTO> findManagers(UUID institutionId, Pageable pageable) {
        return userRepository.findManagers(institutionId, pageable).map(UserUtils::toDTO);
    }

    public Page<UserDTO> findTeachers(UUID institutionId, Pageable pageable) {
        return userRepository.findTeachers(institutionId, pageable).map(UserUtils::toDTO);
    }

    public Page<UserDTO> findStudents(UUID institutionId, Pageable pageable) {
        return userRepository.findStudents(institutionId, pageable).map(UserUtils::toDTO);
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
        return userRepository.findAdminsByName(name, pageable).map(UserUtils::toDTO);
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
}
