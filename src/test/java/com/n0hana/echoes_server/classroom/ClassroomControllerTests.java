package com.n0hana.echoes_server.classroom;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.n0hana.echoes_server.classroom.exception.ClassroomArchivedException;
import com.n0hana.echoes_server.classroom.exception.ClassroomForbiddenException;
import com.n0hana.echoes_server.classroom.exception.ClassroomNotFoundException;
import com.n0hana.echoes_server.infra.security.JwtTokenService;
import com.n0hana.echoes_server.institution.InstitutionModel;
import com.n0hana.echoes_server.user.UserRepository;

@WebMvcTest(ClassroomController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class ClassroomControllerTests {

    private static final String EMAIL = "professor@email.com";
    private static final Principal PRINCIPAL = () -> EMAIL;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClassroomService service;

    @MockitoBean
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    @DisplayName("POST /api/v1/classrooms com dados válidos (201 Created)")
    void shouldCreateClassroom() throws Exception {
        when(service.create(eq(EMAIL), any(ClassroomDTO.Request.class))).thenReturn(classroom("Turma A"));

        mockMvc.perform(post("/api/v1/classrooms")
                .principal(PRINCIPAL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"Turma A\", \"description\": \"Descrição\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Turma A"));
    }

    @Test
    @DisplayName("POST /api/v1/classrooms com título vazio (400 Bad Request)")
    void shouldRejectBlankTitle() throws Exception {
        mockMvc.perform(post("/api/v1/classrooms")
                .principal(PRINCIPAL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("GET /api/v1/classrooms retorna as turmas do usuário (200 OK)")
    void shouldListClassrooms() throws Exception {
        when(service.findAll(eq(EMAIL), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(classroom("Turma A"))));

        mockMvc.perform(get("/api/v1/classrooms").principal(PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Turma A"));
    }

    @Test
    @DisplayName("GET /api/v1/classrooms/{id} turma inexistente (404 Not Found)")
    void shouldReturnNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.findById(EMAIL, id)).thenThrow(new ClassroomNotFoundException());

        mockMvc.perform(get("/api/v1/classrooms/" + id).principal(PRINCIPAL))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Turma não encontrada"));
    }

    @Test
    @DisplayName("GET /api/v1/classrooms/{id} sem acesso (403 Forbidden)")
    void shouldReturnForbidden() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.findById(EMAIL, id)).thenThrow(new ClassroomForbiddenException("Sem acesso a esta turma"));

        mockMvc.perform(get("/api/v1/classrooms/" + id).principal(PRINCIPAL))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /api/v1/classrooms/{id} turma arquivada (409 Conflict)")
    void shouldReturnConflictWhenArchived() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.update(eq(EMAIL), eq(id), any(ClassroomDTO.Request.class)))
                .thenThrow(new ClassroomArchivedException());

        mockMvc.perform(put("/api/v1/classrooms/" + id)
                .principal(PRINCIPAL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"Novo título\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("PATCH /api/v1/classrooms/{id}/archive (204 No Content)")
    void shouldArchiveClassroom() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(patch("/api/v1/classrooms/" + id + "/archive").principal(PRINCIPAL))
                .andExpect(status().isNoContent());

        verify(service).archive(EMAIL, id);
    }

    @Test
    @DisplayName("PATCH /api/v1/classrooms/{id}/unarchive (204 No Content)")
    void shouldUnarchiveClassroom() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(patch("/api/v1/classrooms/" + id + "/unarchive").principal(PRINCIPAL))
                .andExpect(status().isNoContent());

        verify(service).unarchive(EMAIL, id);
    }

    private ClassroomModel classroom(String title) {
        return ClassroomModel.builder()
                .id(UUID.randomUUID())
                .title(title)
                .institution(InstitutionModel.builder().id(UUID.randomUUID()).build())
                .build();
    }
}
