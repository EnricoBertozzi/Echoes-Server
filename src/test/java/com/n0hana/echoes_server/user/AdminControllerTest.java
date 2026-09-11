package com.n0hana.echoes_server.user;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

import com.n0hana.echoes_server.user.dto.CreateUserDTO;
import com.n0hana.echoes_server.user.dto.UpdateUserDTO;
import com.n0hana.echoes_server.user.dto.UserDTO;
import com.n0hana.echoes_server.user.exception.UserNotFoundException;

@WebMvcTest(AdminController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(SpringDataWebConfiguration.class)
@WithMockUser
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService service;

    private final UUID id = UUID.randomUUID();

    @Test
    @DisplayName("POST /users/admin com dados válidos → 201 + UserDTO sem senha")
    void createValidReturns201WithoutPassword() throws Exception {
        when(service.createAdmin(any(CreateUserDTO.class)))
                .thenReturn(new UserDTO(id, "Joao", "joao@example.com", null, UserRole.ADMIN));

        mockMvc.perform(post("/users/admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Joao\",\"email\":\"joao@example.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Joao"))
                .andExpect(jsonPath("$.email").value("joao@example.com"))
                .andExpect(jsonPath("$.institutionId").value(nullValue()))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("POST /users/admin com dados inválidos → 400 + mapa campo→mensagem")
    void createInvalidReturns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/users/admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name").value("O nome é obrigatório"))
                .andExpect(jsonPath("$.email").value("E-mail inválido"));
    }

    @Test
    @DisplayName("GET /users/admin/{id} → 200 + UserDTO sem senha")
    void findByIdReturns200WithoutPassword() throws Exception {
        when(service.findAdminById(id))
                .thenReturn(new UserDTO(id, "Joao", "joao@example.com", null, UserRole.ADMIN));

        mockMvc.perform(get("/users/admin/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Joao"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("GET /users/admin/{id} desconhecido → 404 + mensagem")
    void findUnknownIdReturns404WithMessage() throws Exception {
        when(service.findAdminById(id)).thenThrow(new UserNotFoundException());

        mockMvc.perform(get("/users/admin/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Usuário não encontrado"));
    }

    @Test
    @DisplayName("GET /users/admin → 200 + página com conteúdo")
    void findAllReturns200WithPageContent() throws Exception {
        when(service.findAdmins(any(Pageable.class)))
                .thenReturn(new PageImpl<>(
                        List.of(new UserDTO(id, "Joao", "joao@example.com", null, UserRole.ADMIN)),
                        PageRequest.of(0, 20),
                        1));

        mockMvc.perform(get("/users/admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].name").value("Joao"))
                .andExpect(jsonPath("$.content[0].role").value("ADMIN"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].password").doesNotExist());

        verify(service).findAdmins(any(Pageable.class));
    }

    @Test
    @DisplayName("PATCH /users/admin/{id} válido → 200 + UserDTO sem senha")
    void updateValidReturns200WithoutPassword() throws Exception {
        when(service.updateAdmin(eq(id), any(UpdateUserDTO.class)))
                .thenReturn(new UserDTO(id, "Joao Silva", "joao@example.com", null, UserRole.ADMIN));

        mockMvc.perform(patch("/users/admin/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Joao Silva\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Joao Silva"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("DELETE /users/admin/{id} → 204 e delega ao serviço")
    void deleteReturns204AndDelegates() throws Exception {
        mockMvc.perform(delete("/users/admin/{id}", id))
                .andExpect(status().isNoContent());

        verify(service).deleteAdmin(id);
    }
}
