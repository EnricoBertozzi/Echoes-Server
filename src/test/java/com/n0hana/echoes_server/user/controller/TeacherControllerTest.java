package com.n0hana.echoes_server.user.controller;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.n0hana.echoes_server.infra.security.JwtTokenService;
import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.dto.CreateInstitutionUserDTO;
import com.n0hana.echoes_server.user.dto.PendingRegistrationDTO;
import com.n0hana.echoes_server.user.dto.UpdateInstitutionUserDTO;
import com.n0hana.echoes_server.user.dto.UserDTO;
import com.n0hana.echoes_server.user.exception.UserExceptionHandler;
import com.n0hana.echoes_server.user.exception.UserNotFoundException;
import com.n0hana.echoes_server.user.model.UserRole;
import com.n0hana.echoes_server.user.service.UserInfoService;
import com.n0hana.echoes_server.user.service.UserManagerService;
import com.n0hana.echoes_server.user.service.UserRegisterService;

@WebMvcTest(TeacherController.class)
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser
@ActiveProfiles("test")

@Import({SpringDataWebConfiguration.class, UserExceptionHandler.class})
class TeacherControllerTest {

   @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRegisterService userRegisterService;

    @MockitoBean 
    private UserInfoService userInfoService;

    @MockitoBean
    private UserManagerService userManagerService;
    
    @MockitoBean private JwtTokenService jwtTokenService;
    @MockitoBean private UserRepository userRepository;


    private final UUID id = UUID.randomUUID();
    private final UUID institutionId = UUID.randomUUID();

    @Test
    @DisplayName("POST /api/v1/users/teacher com dados válidos → 202 + pendência com institutionId, sem id e sem código")
    void createValidReturns202PendingRegistration() throws Exception {
        when(userRegisterService.createTeacher(any(CreateInstitutionUserDTO.class)))
                .thenReturn(new PendingRegistrationDTO("Professor", "professor@example.com", UserRole.TEACHER, institutionId));

        mockMvc.perform(post("/api/v1/users/teacher")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Professor\",\"email\":\"professor@example.com\","
                                + "\"institutionId\":\"" + institutionId + "\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.name").value("Professor"))
                .andExpect(jsonPath("$.email").value("professor@example.com"))
                .andExpect(jsonPath("$.institutionId").value(institutionId.toString()))
                .andExpect(jsonPath("$.role").value("TEACHER"))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    @DisplayName("POST /api/v1/users/teacher com dados inválidos → 400 + mapa campo→mensagem")
    void createInvalidReturns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/v1/users/teacher")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name").value("O nome é obrigatório"))
                .andExpect(jsonPath("$.email").value("E-mail inválido"));
    }

    @Test
    @DisplayName("GET /api/v1/users/teacher/{id} → 200 + UserDTO sem senha")
    void findByIdReturns200WithoutPassword() throws Exception {
        when(userInfoService.findTeacherById(id))
                .thenReturn(new UserDTO(id, "Professor", "professor@example.com", institutionId, UserRole.TEACHER));

        mockMvc.perform(get("/api/v1/users/teacher/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Professor"))
                .andExpect(jsonPath("$.role").value("TEACHER"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/v1/users/teacher/{id} desconhecido → 404 + mensagem")
    void findUnknownIdReturns404WithMessage() throws Exception {
        when(userInfoService.findTeacherById(id)).thenThrow(new UserNotFoundException());

        mockMvc.perform(get("/api/v1/users/teacher/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Usuário não encontrado"));
    }

    @Test
    @DisplayName("GET /api/v1/users/teacher com institutionId → 200 e repassa o filtro ao serviço")
    void findAllWithInstitutionIdFilter() throws Exception {
        when(userInfoService.findTeachers(eq(institutionId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(
                        List.of(new UserDTO(id, "Professor", "professor@example.com", institutionId, UserRole.TEACHER)),
                        PageRequest.of(0, 20),
                        1));

        mockMvc.perform(get("/api/v1/users/teacher").param("institutionId", institutionId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].role").value("TEACHER"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].password").doesNotExist());

        verify(userInfoService).findTeachers(eq(institutionId), any(Pageable.class));
    }

    @Test
    @DisplayName("GET /api/v1/users/teacher sem institutionId → 200 e chama o serviço com filtro nulo")
    void findAllWithoutInstitutionIdFilter() throws Exception {
        when(userInfoService.findTeachers(isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(
                        List.of(new UserDTO(id, "Professor", "professor@example.com", institutionId, UserRole.TEACHER)),
                        PageRequest.of(0, 20),
                        1));

        mockMvc.perform(get("/api/v1/users/teacher"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].password").doesNotExist());

        verify(userInfoService).findTeachers(isNull(), any(Pageable.class));
    }

    @Test
    @DisplayName("PATCH /api/v1/users/teacher/{id} válido → 200 + UserDTO sem senha")
    void updateValidReturns200WithoutPassword() throws Exception {
        when(userManagerService.updateTeacher(eq(id), any(UpdateInstitutionUserDTO.class)))
                .thenReturn(new UserDTO(id, "Professor Silva", "professor@example.com", institutionId, UserRole.TEACHER));

        mockMvc.perform(patch("/api/v1/users/teacher/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Professor Silva\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Professor Silva"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("DELETE /api/v1/users/teacher/{id} → 204 e delega ao serviço")
    void deleteReturns204AndDelegates() throws Exception {
        mockMvc.perform(delete("/api/v1/users/teacher/{id}", id))
                .andExpect(status().isNoContent());

        verify(userManagerService).deleteTeacher(id);
    }
}
