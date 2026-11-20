package com.n0hana.echoes_server.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.n0hana.echoes_server.classroom.ClassroomMemberRepository;
import com.n0hana.echoes_server.institution.InstitutionModel;
import com.n0hana.echoes_server.institution.InstitutionService;
import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.dto.UpdateInstitutionUserDTO;
import com.n0hana.echoes_server.user.dto.UpdateUserDTO;
import com.n0hana.echoes_server.user.dto.UserDTO;
import com.n0hana.echoes_server.user.exception.EmailAlreadyInUseException;
import com.n0hana.echoes_server.user.exception.LastManagerException;
import com.n0hana.echoes_server.user.exception.UserNotFoundException;
import com.n0hana.echoes_server.user.model.Admin;
import com.n0hana.echoes_server.user.model.Manager;

@ExtendWith(MockitoExtension.class)
public class UserManagerServiceTests {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserManagerService userService;

    @Mock
    private InstitutionService institutionService;

    @Mock
    private ClassroomMemberRepository classroomMemberRepository;
    @InjectMocks
    private UserInfoService userInfoService;

    @Test
    @DisplayName("PATCH apenas com nome: e-mail permanece inalterado")
    void patchNameOnlyKeepsEmail() {
        Admin admin = newAdmin("João", "joao@example.com");
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(userRepository.save(any(Admin.class))).thenAnswer(inv -> inv.getArgument(0));

        UserDTO dto = userService.updateAdmin(admin.getId(), new UpdateUserDTO("João Silva"));

        assertEquals("João Silva", dto.name());
        assertEquals("joao@example.com", dto.email());
        assertEquals("joao@example.com", admin.getEmail());
    }

    @Test
    @DisplayName("PATCH apenas com institutionId (Manager): nome e e-mail permanecem inalterados")
    void patchInstitutionIdOnlyKeepsNameAndEmail() {
        UUID newInstitutionId = UUID.randomUUID();

        Manager manager = newManager("Gestor", "gestor@example.com",
                UUID.randomUUID());

        InstitutionModel newInstitution = new InstitutionModel();
        newInstitution.setId(newInstitutionId);

        when(userRepository.findById(manager.getId())).thenReturn(Optional.of(manager));
        when(userRepository.save(any(Manager.class))).thenAnswer(inv -> inv.getArgument(0));
        when(institutionService.findById(newInstitutionId)).thenReturn(newInstitution);
        // a instituição de origem tem outro gestor, então ele pode sair
        when(userRepository.countActiveManagers(manager.getInstitution().getId())).thenReturn(2L);
        UserDTO dto = userService.updateManager(manager.getId(),
                new UpdateInstitutionUserDTO(null, newInstitutionId));

        assertEquals(newInstitutionId, dto.institutionId());
        assertEquals("Gestor", dto.name());
        assertEquals("gestor@example.com", dto.email());
    }

    @Test
    @DisplayName("PATCH totalmente nulo: nada muda, mas ainda salva e retorna DTO")
    void patchAllNullChangesNothingButStillSaves() {
        Admin admin = newAdmin("João", "joao@example.com");
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(userRepository.save(any(Admin.class))).thenAnswer(inv -> inv.getArgument(0));

        UserDTO dto = userService.updateAdmin(admin.getId(), new UpdateUserDTO(null));

        assertEquals("João", dto.name());
        assertEquals("joao@example.com", dto.email());
        verify(userRepository).save(admin);
    }

    @Test
    @DisplayName("Remoção: inativa (active=false) e salva; usuário já inativo lança UserNotFoundException")
    void deleteSoftDeletesAndInactiveUserIsNotFound() {
        Manager manager = newManager("Gestor", "gestor@example.com", UUID.randomUUID());
        when(userRepository.findById(manager.getId())).thenReturn(Optional.of(manager));
        when(userRepository.countActiveManagers(manager.getInstitution().getId())).thenReturn(2L);
        userService.deleteManager(manager.getId());

        ArgumentCaptor<Manager> userCaptor = ArgumentCaptor.forClass(Manager.class);
        verify(userRepository).save(userCaptor.capture());
        assertFalse(userCaptor.getValue().isActive());
        verify(classroomMemberRepository).deleteByUserId(manager.getId());

        Admin inactiveAdmin = newAdmin("João", "joao@example.com");
        inactiveAdmin.setActive(false);
        when(userRepository.findById(inactiveAdmin.getId())).thenReturn(Optional.of(
                inactiveAdmin));
        assertThrows(UserNotFoundException.class, () -> userInfoService.findAdminById(inactiveAdmin.getId()));
    }

    @Test
    @DisplayName("Remoção do último gestor da instituição lança LastManagerException")
    void lastManagerCannotBeDeleted() {
        Manager manager = newManager("Gestor", "gestor@example.com", UUID.randomUUID());
        when(userRepository.findById(manager.getId())).thenReturn(Optional.of(manager));
        when(userRepository.countActiveManagers(manager.getInstitution().getId())).thenReturn(1L);

        assertThrows(LastManagerException.class, () -> userService.deleteManager(manager.getId()));

        assertTrue(manager.isActive());
        verify(userRepository, never()).save(any());
        verify(classroomMemberRepository, never()).deleteByUserId(any());
    }

    @Test
    @DisplayName("Último gestor não troca de instituição")
    void lastManagerCannotMoveToOtherInstitution() {
        UUID newInstitutionId = UUID.randomUUID();
        Manager manager = newManager("Gestor", "gestor@example.com", UUID.randomUUID());
        InstitutionModel newInstitution = new InstitutionModel();
        newInstitution.setId(newInstitutionId);
        when(userRepository.findById(manager.getId())).thenReturn(Optional.of(manager));
        when(institutionService.findById(newInstitutionId)).thenReturn(newInstitution);
        when(userRepository.countActiveManagers(manager.getInstitution().getId())).thenReturn(1L);

        assertThrows(LastManagerException.class, () -> userService.updateManager(manager.getId(),
                new UpdateInstitutionUserDTO(null, newInstitutionId)));

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Gestor mantém a própria instituição no PATCH sem checar a contagem")
    void managerKeepingSameInstitutionIsNotBlocked() {
        UUID institutionId = UUID.randomUUID();
        Manager manager = newManager("Gestor", "gestor@example.com", institutionId);
        InstitutionModel sameInstitution = new InstitutionModel();
        sameInstitution.setId(institutionId);
        when(userRepository.findById(manager.getId())).thenReturn(Optional.of(manager));
        when(userRepository.save(any(Manager.class))).thenAnswer(inv -> inv.getArgument(0));
        when(institutionService.findById(institutionId)).thenReturn(sameInstitution);

        userService.updateManager(manager.getId(), new UpdateInstitutionUserDTO(null, institutionId));

        verify(userRepository, never()).countActiveManagers(any());
    }

    @Test
    @DisplayName("PATCH /me atualiza só o nome; e-mail não é editável (S11)")
    void updateCurrentUserUpdatesNameOnly() {
        // Arrange
        Admin admin = newAdmin("João", "joao@example.com");
        when(userRepository.findByEmail("joao@example.com")).thenReturn(Optional.of(admin));
        when(userRepository.save(any(Admin.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        UserDTO dto = userService.updateCurrentUser("joao@example.com",
                new UpdateUserDTO("João Silva"));

        // Assert
        assertEquals("João Silva", dto.name());
        assertEquals("joao@example.com", dto.email());
        assertEquals("joao@example.com", admin.getEmail());
        verify(userRepository).save(admin);
    }

    @Test
    @DisplayName("DELETE /me remove logicamente o usuário autenticado")
    void deleteCurrentUserSoftDeletesAuthenticatedUser() {
        // Arrange
        Manager manager = newManager("Gestor", "gestor@example.com", UUID.randomUUID());
        when(userRepository.findByEmail("gestor@example.com")).thenReturn(Optional.of(manager));
        when(userRepository.countActiveManagers(manager.getInstitution().getId())).thenReturn(2L);

        // Act
        userService.deleteCurrentUser("gestor@example.com");

        // Assert
        ArgumentCaptor<Manager> userCaptor = ArgumentCaptor.forClass(Manager.class);
        verify(userRepository).save(userCaptor.capture());
        Manager deletedUser = userCaptor.getValue();

        assertFalse(deletedUser.isActive());
        assertNull(deletedUser.getName());
        assertNotNull(deletedUser.getEmail());
        assertNotEquals("gestor@example.com", deletedUser.getEmail());
        assertNull(deletedUser.getPassword());
        assertEquals(0, deletedUser.getLoginAttempts());
        assertNull(deletedUser.getLockUntil());
        assertNull(deletedUser.getInstitution());
        verify(classroomMemberRepository).deleteByUserId(manager.getId());
    }

    @Test
    @DisplayName("DELETE /me não permite excluir o último gestor da instituição")
    void deleteCurrentUserCannotDeleteLastManager() {
        // Arrange
        Manager manager = newManager("Gestor", "gestor@example.com", UUID.randomUUID());
        when(userRepository.findByEmail("gestor@example.com")).thenReturn(Optional.of(manager));
        when(userRepository.countActiveManagers(manager.getInstitution().getId())).thenReturn(1L);

        // Act e Assert
        assertThrows(LastManagerException.class, () -> userService.deleteCurrentUser("gestor@example.com"));

        assertTrue(manager.isActive());
        verify(userRepository, never()).save(any());
        verify(classroomMemberRepository, never()).deleteByUserId(any());
    }

    private Admin newAdmin(String name, String email) {
        Admin admin = new Admin();
        admin.setId(UUID.randomUUID());
        admin.setName(name);
        admin.setEmail(email);
        return admin;
    }

    private Manager newManager(String name, String email, UUID institutionId) {
        InstitutionModel institution = new InstitutionModel();
        institution.setId(institutionId);

        Manager manager = new Manager();
        manager.setId(UUID.randomUUID());
        manager.setName(name);
        manager.setEmail(email);
        manager.setInstitution(institution);
        return manager;
    }
}
