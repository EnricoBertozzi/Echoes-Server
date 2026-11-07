package com.n0hana.echoes_server.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.n0hana.echoes_server.institution.InstitutionModel;
import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.dto.UserDTO;
import com.n0hana.echoes_server.user.exception.InvalidUserTypeException;
import com.n0hana.echoes_server.user.exception.UserNotFoundException;
import com.n0hana.echoes_server.user.model.Admin;
import com.n0hana.echoes_server.user.model.Teacher;
import com.n0hana.echoes_server.user.model.User;
import com.n0hana.echoes_server.user.model.UserRole;

@ExtendWith(MockitoExtension.class)
public class UserInfoServiceTests {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserInfoService userService;

    private final Pageable pageable = PageRequest.of(0, 10);

    @Test
    @DisplayName("Filtro por institutionId é repassado ao repositório; null significa sem filtro")
    void institutionIdFilterIsPassedThroughToRepository() {
        UUID institutionId = UUID.randomUUID();
        when(userRepository.findManagers(institutionId,
                pageable)).thenReturn(Page.empty());
        when(userRepository.findManagers(null, pageable)).thenReturn(Page.empty());

        userService.findManagers(institutionId, pageable);
        userService.findManagers(null, pageable);

        verify(userRepository).findManagers(institutionId, pageable);
        verify(userRepository).findManagers(null, pageable);
    }

    @Test
    @DisplayName("Paginação: página de entidades é mapeada para Page<UserDTO> com papel e institutionId corretos")
    void pageOfEntitiesIsMappedToPageOfUserDTO() {
        Admin admin1 = newAdmin("Admin Um", "um@example.com");
        Admin admin2 = newAdmin("Admin Dois", "dois@example.com");
        when(userRepository.findAdmins(pageable))
                .thenReturn(new PageImpl<>(List.of(admin1, admin2), pageable, 2));

        Page<UserDTO> result = userService.findAdmins(pageable);

        assertEquals(2, result.getContent().size());
        assertEquals(2, result.getTotalElements());
        UserDTO first = result.getContent().get(0);
        assertEquals(admin1.getId(), first.id());
        assertEquals("Admin Um", first.name());
        assertNull(first.institutionId());
        assertEquals(UserRole.ADMIN, first.role());
    }

    @Test
    @DisplayName("Tipo errado: buscar Manager que é Admin, ou Student que é Teacher, lança InvalidUserTypeException")
    void wrongUserTypeThrowsInvalidUserTypeException() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.of(newAdmin("João",
                "joao@example.com")));
        assertThrows(InvalidUserTypeException.class, () -> userService.findManagerById(id));

        when(userRepository.findById(id)).thenReturn(Optional.of(newTeacher("Prof",
                "prof@example.com", null)));
        assertThrows(InvalidUserTypeException.class, () -> userService.findStudentById(id));
    }

    private Admin newAdmin(String name, String email) {
        Admin admin = new Admin();
        admin.setId(UUID.randomUUID());
        admin.setName(name);
        admin.setEmail(email);
        return admin;
    }

    private Teacher newTeacher(String name, String email, UUID institutionId) {
        InstitutionModel institution = new InstitutionModel();
        institution.setId(institutionId);

        Teacher teacher = new Teacher();
        teacher.setId(UUID.randomUUID());
        teacher.setName(name);
        teacher.setEmail(email);
        teacher.setInstitution(institution);
        return teacher;
    }

    @Test
    @DisplayName("Deve buscar um usuáro pelo e-mail")
    void shouldFindUserByEmail() {
        // Arrange
        String email = "teste@email.com";
        User user = mock(User.class);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        // Act
        userService.findUserByEmail(email);

        // Assert
        verify(userRepository).findByEmail(email);
    }

    @Test
    @DisplayName("Deve lançar UserNotFoundException quando o usuário não for encontrado pelo e-mail")
    void shouldThrowUserNotFoundExceptionWhenUserNotFoundByEmail() {
        // Arrange
        String email = "teste@email.com";

        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());
        
        // Act e Assert
        assertThrows(UserNotFoundException.class, () -> userService.findUserByEmail(email));
    }
}
