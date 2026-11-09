package com.n0hana.echoes_server.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

import com.n0hana.echoes_server.institution.InstitutionModel;
import com.n0hana.echoes_server.institution.InstitutionService;
import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.dto.UpdateInstitutionUserDTO;
import com.n0hana.echoes_server.user.dto.UpdateUserDTO;
import com.n0hana.echoes_server.user.dto.UserDTO;
import com.n0hana.echoes_server.user.exception.EmailAlreadyInUseException;
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

    @InjectMocks
    private UserInfoService userInfoService;

    @Test
    @DisplayName("PATCH apenas com nome: e-mail permanece inalterado")
    void patchNameOnlyKeepsEmail() {
        Admin admin = newAdmin("João", "joao@example.com");
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(userRepository.save(any(Admin.class))).thenAnswer(inv -> inv.getArgument(0));

        UserDTO dto = userService.updateAdmin(admin.getId(), new UpdateUserDTO("João Silva", null));

        assertEquals("João Silva", dto.name());
        assertEquals("joao@example.com", dto.email());
        assertEquals("joao@example.com", admin.getEmail());
    }

    @Test
    @DisplayName("PATCH apenas com e-mail: nome permanece inalterado e e-mail é normalizado")
    void patchEmailOnlyKeepsName() {
        Admin admin = newAdmin("João", "joao@example.com");
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(userRepository.existsByEmail("novo@example.com")).thenReturn(false);
        when(userRepository.save(any(Admin.class))).thenAnswer(inv -> inv.getArgument(0));

        UserDTO dto = userService.updateAdmin(admin.getId(), new UpdateUserDTO(null,
                "  NOVO@Example.COM "));

        assertEquals("novo@example.com", dto.email());
        assertEquals("João", dto.name());
        assertEquals("novo@example.com", admin.getEmail());
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

        UserDTO dto = userService.updateManager(manager.getId(),
                new UpdateInstitutionUserDTO(null, null, newInstitutionId));

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

        UserDTO dto = userService.updateAdmin(admin.getId(), new UpdateUserDTO(null,
                null));

        assertEquals("João", dto.name());
        assertEquals("joao@example.com", dto.email());
        verify(userRepository).save(admin);
    }

    @Test
    @DisplayName("Remoção: inativa (active=false) e salva; usuário já inativo lança UserNotFoundException")
    void deleteSoftDeletesAndInactiveUserIsNotFound() {
        Manager manager = newManager("Gestor", "gestor@example.com", null);
        when(userRepository.findById(manager.getId())).thenReturn(Optional.of(manager));

        userService.deleteManager(manager.getId());

        ArgumentCaptor<Manager> userCaptor = ArgumentCaptor.forClass(Manager.class);
        verify(userRepository).save(userCaptor.capture());
        assertFalse(userCaptor.getValue().isActive());

        Admin inactiveAdmin = newAdmin("João", "joao@example.com");
        inactiveAdmin.setActive(false);
        when(userRepository.findById(inactiveAdmin.getId())).thenReturn(Optional.of(
                inactiveAdmin));
        assertThrows(UserNotFoundException.class, () -> userInfoService.findAdminById(inactiveAdmin.getId()));
    }

    @Test
    @DisplayName("Update para e-mail já usado lança EmailAlreadyInUseException; mesmo e-mail (no-op) é aceito sem verificação")
    void updateToUsedEmailThrowsAndSameEmailIsAcceptedWithoutCheck() {
        Admin admin = newAdmin("João", "joao@example.com");
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(userRepository.existsByEmail("novo@example.com")).thenReturn(true);
        when(userRepository.save(any(Admin.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThrows(EmailAlreadyInUseException.class, () -> userService.updateAdmin(
                admin.getId(), new UpdateUserDTO(null, "novo@example.com")));

        UserDTO dto = userService.updateAdmin(admin.getId(), new UpdateUserDTO(null,
                "JOAO@example.com"));

        assertEquals("joao@example.com", dto.email());
        verify(userRepository, times(1)).existsByEmail(anyString());
        verify(userRepository, never()).existsByEmail("joao@example.com");
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
