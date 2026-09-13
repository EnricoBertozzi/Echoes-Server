package com.n0hana.echoes_server.user;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.config.SpringDataWebConfiguration;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.n0hana.echoes_server.user.dto.CreateInstitutionUserDTO;
import com.n0hana.echoes_server.user.dto.PendingRegistrationDTO;
import com.n0hana.echoes_server.user.dto.UpdateInstitutionUserDTO;
import com.n0hana.echoes_server.user.dto.UserDTO;
import com.n0hana.echoes_server.user.exception.UserNotFoundException;

@WebMvcTest(ManagerController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(SpringDataWebConfiguration.class)
@WithMockUser
class ManagerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService service;

    private final UUID id = UUID.randomUUID();
    private final UUID institutionId = UUID.randomUUID();

    @Test
    @DisplayName("POST /users/manager com dados válidos → 202 + pendência com institutionId, sem id e sem código")
    void createValidReturns202PendingRegistration() throws Exception {
        when(service.createManager(any(CreateInstitutionUserDTO.class)))
                .thenReturn(new PendingRegistrationDTO("Gestor", "gestor@example.com", UserRole.MANAGER, institutionId));

        mockMvc.perform(post("/users/manager")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Gestor\",\"email\":\"gestor@example.com\","
                                + "\"institutionId\":\"" + institutionId + "\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.name").value("Gestor"))
                .andExpect(jsonPath("$.email").value("gestor@example.com"))
                .andExpect(jsonPath("$.institutionId").value(institutionId.toString()))
                .andExpect(jsonPath("$.role").value("MANAGER"))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    @DisplayName("POST /users/manager com dados inválidos → 400 + mapa campo→mensagem")
    void createInvalidReturns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/users/manager")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name").value("O nome é obrigatório"))
                .andExpect(jsonPath("$.email").value("E-mail inválido"));
    }

    @Test
    @DisplayName("GET /users/manager/{id} → 200 + UserDTO sem senha")
    void findByIdReturns200WithoutPassword() throws Exception {
        when(service.findManagerById(id))
                .thenReturn(new UserDTO(id, "Gestor", "gestor@example.com", institutionId, UserRole.MANAGER));

        mockMvc.perform(get("/users/manager/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Gestor"))
                .andExpect(jsonPath("$.role").value("MANAGER"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("GET /users/manager/{id} desconhecido → 404 + mensagem")
    void findUnknownIdReturns404WithMessage() throws Exception {
        when(service.findManagerById(id)).thenThrow(new UserNotFoundException());

        mockMvc.perform(get("/users/manager/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Usuário não encontrado"));
    }

    @Test
    @DisplayName("GET /users/manager com institutionId → 200 e repassa o filtro ao serviço")
    void findAllWithInstitutionIdFilter() throws Exception {
        when(service.findManagers(eq(institutionId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(
                        List.of(new UserDTO(id, "Gestor", "gestor@example.com", institutionId, UserRole.MANAGER)),
                        PageRequest.of(0, 20),
                        1));

        mockMvc.perform(get("/users/manager").param("institutionId", institutionId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].role").value("MANAGER"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].password").doesNotExist());

        verify(service).findManagers(eq(institutionId), any(Pageable.class));
    }

    @Test
    @DisplayName("GET /users/manager sem institutionId → 200 e chama o serviço com filtro nulo")
    void findAllWithoutInstitutionIdFilter() throws Exception {
        when(service.findManagers(isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(
                        List.of(new UserDTO(id, "Gestor", "gestor@example.com", institutionId, UserRole.MANAGER)),
                        PageRequest.of(0, 20),
                        1));

        mockMvc.perform(get("/users/manager"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].password").doesNotExist());

        verify(service).findManagers(isNull(), any(Pageable.class));
    }

    @Test
    @DisplayName("PATCH /users/manager/{id} válido → 200 + UserDTO sem senha")
    void updateValidReturns200WithoutPassword() throws Exception {
        when(service.updateManager(eq(id), any(UpdateInstitutionUserDTO.class)))
                .thenReturn(new UserDTO(id, "Gestor Silva", "gestor@example.com", institutionId, UserRole.MANAGER));

        mockMvc.perform(patch("/users/manager/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Gestor Silva\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Gestor Silva"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("PATCH /users/manager/{id} com nome/e-mail em branco → 400 de validação")
    void patchWithBlankFieldsReturns400() throws Exception {
        mockMvc.perform(patch("/users/manager/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"email\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name").value("O nome não pode ficar em branco"))
                .andExpect(jsonPath("$.email").value("O e-mail não pode ficar em branco"));
    }

    @Test
    @DisplayName("DELETE /users/manager/{id} → 204 e delega ao serviço")
    void deleteReturns204AndDelegates() throws Exception {
        mockMvc.perform(delete("/users/manager/{id}", id))
                .andExpect(status().isNoContent());

        verify(service).deleteManager(id);
    }
}
