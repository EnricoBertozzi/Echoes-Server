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

@WebMvcTest(StudentController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(SpringDataWebConfiguration.class)
@WithMockUser
class StudentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService service;

    private final UUID id = UUID.randomUUID();
    private final UUID institutionId = UUID.randomUUID();

    @Test
    @DisplayName("POST /users/student com dados válidos → 202 + pendência com institutionId, sem id e sem código")
    void createValidReturns202PendingRegistration() throws Exception {
        when(service.createStudent(any(CreateInstitutionUserDTO.class)))
                .thenReturn(new PendingRegistrationDTO("Aluno", "aluno@example.com", UserRole.STUDENT, institutionId));

        mockMvc.perform(post("/users/student")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Aluno\",\"email\":\"aluno@example.com\","
                                + "\"institutionId\":\"" + institutionId + "\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.name").value("Aluno"))
                .andExpect(jsonPath("$.email").value("aluno@example.com"))
                .andExpect(jsonPath("$.institutionId").value(institutionId.toString()))
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    @DisplayName("POST /users/student com dados inválidos → 400 + mapa campo→mensagem")
    void createInvalidReturns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/users/student")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name").value("O nome é obrigatório"))
                .andExpect(jsonPath("$.email").value("E-mail inválido"));
    }

    @Test
    @DisplayName("GET /users/student/{id} → 200 + UserDTO sem senha")
    void findByIdReturns200WithoutPassword() throws Exception {
        when(service.findStudentById(id))
                .thenReturn(new UserDTO(id, "Aluno", "aluno@example.com", institutionId, UserRole.STUDENT));

        mockMvc.perform(get("/users/student/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Aluno"))
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("GET /users/student/{id} desconhecido → 404 + mensagem")
    void findUnknownIdReturns404WithMessage() throws Exception {
        when(service.findStudentById(id)).thenThrow(new UserNotFoundException());

        mockMvc.perform(get("/users/student/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Usuário não encontrado"));
    }

    @Test
    @DisplayName("GET /users/student com institutionId → 200 e repassa o filtro ao serviço")
    void findAllWithInstitutionIdFilter() throws Exception {
        when(service.findStudents(eq(institutionId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(
                        List.of(new UserDTO(id, "Aluno", "aluno@example.com", institutionId, UserRole.STUDENT)),
                        PageRequest.of(0, 20),
                        1));

        mockMvc.perform(get("/users/student").param("institutionId", institutionId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].role").value("STUDENT"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].password").doesNotExist());

        verify(service).findStudents(eq(institutionId), any(Pageable.class));
    }

    @Test
    @DisplayName("GET /users/student sem institutionId → 200 e chama o serviço com filtro nulo")
    void findAllWithoutInstitutionIdFilter() throws Exception {
        when(service.findStudents(isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(
                        List.of(new UserDTO(id, "Aluno", "aluno@example.com", institutionId, UserRole.STUDENT)),
                        PageRequest.of(0, 20),
                        1));

        mockMvc.perform(get("/users/student"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].password").doesNotExist());

        verify(service).findStudents(isNull(), any(Pageable.class));
    }

    @Test
    @DisplayName("PATCH /users/student/{id} válido → 200 + UserDTO sem senha")
    void updateValidReturns200WithoutPassword() throws Exception {
        when(service.updateStudent(eq(id), any(UpdateInstitutionUserDTO.class)))
                .thenReturn(new UserDTO(id, "Aluno Silva", "aluno@example.com", institutionId, UserRole.STUDENT));

        mockMvc.perform(patch("/users/student/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Aluno Silva\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Aluno Silva"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("DELETE /users/student/{id} → 204 e delega ao serviço")
    void deleteReturns204AndDelegates() throws Exception {
        mockMvc.perform(delete("/users/student/{id}", id))
                .andExpect(status().isNoContent());

        verify(service).deleteStudent(id);
    }
}
