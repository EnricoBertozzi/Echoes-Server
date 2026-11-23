package com.n0hana.echoes_server.user.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.n0hana.echoes_server.user.dto.CreateUserDTO;
import com.n0hana.echoes_server.user.dto.PendingInvitationDTO;
import com.n0hana.echoes_server.user.dto.UpdateUserDTO;
import com.n0hana.echoes_server.user.dto.UserDTO;
import com.n0hana.echoes_server.user.dto.UserEmailDTO;
import com.n0hana.echoes_server.user.exception.UserNotFoundException;
import com.n0hana.echoes_server.user.model.UserRole;
import com.n0hana.echoes_server.user.service.UserInfoService;
import com.n0hana.echoes_server.user.service.UserManagerService;
import com.n0hana.echoes_server.user.service.UserRegisterService;

@ExtendWith(MockitoExtension.class)
class UserControllersCoverageTests {

    @Mock
    private UserInfoService userInfoService;
    @Mock
    private UserRegisterService userRegisterService;
    @Mock
    private UserManagerService userManagerService;

    private static UserDTO dto(UUID id) {
        return new UserDTO(id, "Admin", "Silva", "a***@x.com", null, UserRole.ADMIN, false);
    }

    // ------------------------------------------------------------ AdminController

    @Test
    @DisplayName("AdminController.create envia o convite e responde 202 sem corpo")
    void adminCreate() {
        AdminController controller = new AdminController(userInfoService, userRegisterService, userManagerService);
        CreateUserDTO body = new CreateUserDTO("Ana", "Silva", "ana@example.com");

        ResponseEntity<Void> response = controller.create(body);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertNull(response.getBody());
        verify(userRegisterService).createAdmin(body);
    }

    @Test
    @DisplayName("AdminController consulta (listar, id, busca por nome) delega ao UserInfoService")
    void adminQueries() {
        AdminController controller = new AdminController(userInfoService, userRegisterService, userManagerService);
        UUID id = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 5);
        Page<UserDTO> page = new PageImpl<>(List.of(dto(id)));
        when(userInfoService.findAdmins(pageable)).thenReturn(page);
        when(userInfoService.findAdminById(id)).thenReturn(dto(id));
        when(userInfoService.findAdminByName("An", pageable)).thenReturn(page);

        assertSame(page, controller.findAll(pageable).getBody());
        assertEquals(id, controller.findById(id).getBody().id());
        assertSame(page, controller.findUsersByName("An", pageable).getBody());
    }

    @Test
    @DisplayName("AdminController.findById propaga UserNotFoundException")
    void adminFindByIdNotFound() {
        AdminController controller = new AdminController(userInfoService, userRegisterService, userManagerService);
        UUID id = UUID.randomUUID();
        when(userInfoService.findAdminById(id)).thenThrow(new UserNotFoundException());

        assertThrows(UserNotFoundException.class, () -> controller.findById(id));
    }

    @Test
    @DisplayName("AdminController.update e delete delegam ao UserManagerService")
    void adminUpdateAndDelete() {
        AdminController controller = new AdminController(userInfoService, userRegisterService, userManagerService);
        UUID id = UUID.randomUUID();
        UpdateUserDTO body = new UpdateUserDTO("Novo", null);
        when(userManagerService.updateAdmin(id, body)).thenReturn(dto(id));

        ResponseEntity<UserDTO> updated = controller.update(id, body);
        ResponseEntity<Void> deleted = controller.delete(id);

        assertEquals(HttpStatus.OK, updated.getStatusCode());
        assertEquals(id, updated.getBody().id());
        assertEquals(HttpStatus.NO_CONTENT, deleted.getStatusCode());
        verify(userManagerService).deleteAdmin(id);
    }

    // ------------------------------------------------------------ PendingInvitationController

    @Test
    @DisplayName("PendingInvitationController.list usa o nome do principal e o filtro de instituição")
    void pendingInvitationList() {
        PendingInvitationController controller = new PendingInvitationController(userRegisterService);
        UUID institutionId = UUID.randomUUID();
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn("gestor@example.com");
        PendingInvitationDTO invite = new PendingInvitationDTO("Beto", "Souza", "beto@example.com", UserRole.STUDENT,
            institutionId, Instant.parse("2025-01-01T00:00:00Z"), Instant.parse("2025-01-08T00:00:00Z"));
        when(userRegisterService.listPending("gestor@example.com", institutionId)).thenReturn(List.of(invite));
        when(userRegisterService.listPending("gestor@example.com", null)).thenReturn(List.of());

        ResponseEntity<List<PendingInvitationDTO>> filtered = controller.list(institutionId, principal);
        ResponseEntity<List<PendingInvitationDTO>> all = controller.list(null, principal);

        assertEquals(HttpStatus.OK, filtered.getStatusCode());
        assertEquals(List.of(invite), filtered.getBody());
        assertTrue(all.getBody().isEmpty());
    }

    // ------------------------------------------------------------ UserEmailController

    @Test
    @DisplayName("UserEmailController.findEmail devolve o e-mail completo do serviço auditado")
    void userEmailFindEmail() {
        UserEmailController controller = new UserEmailController(userInfoService);
        UUID id = UUID.randomUUID();
        UserEmailDTO body = new UserEmailDTO(id, "Ana", "ana@example.com", null);
        when(userInfoService.findEmail(id)).thenReturn(body);

        ResponseEntity<UserEmailDTO> response = controller.findEmail(id);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(body, response.getBody());
    }

    @Test
    @DisplayName("UserEmailController.findEmail propaga UserNotFoundException")
    void userEmailNotFound() {
        UserEmailController controller = new UserEmailController(userInfoService);
        UUID id = UUID.randomUUID();
        when(userInfoService.findEmail(id)).thenThrow(new UserNotFoundException());

        assertThrows(UserNotFoundException.class, () -> controller.findEmail(id));
    }
}
