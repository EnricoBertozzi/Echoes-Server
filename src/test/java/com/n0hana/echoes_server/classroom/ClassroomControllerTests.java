package com.n0hana.echoes_server.classroom;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import com.n0hana.echoes_server.classroom.exception.ClassroomCodeActiveException;
import com.n0hana.echoes_server.classroom.exception.ClassroomForbiddenException;
import com.n0hana.echoes_server.classroom.exception.ClassroomNotFoundException;
import com.n0hana.echoes_server.classroom.exception.ClassroomStudentEnrolledException;
import com.n0hana.echoes_server.infra.security.JwtTokenService;
import com.n0hana.echoes_server.institution.InstitutionModel;
import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.model.UserRole;

@WebMvcTest(ClassroomController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class ClassroomControllerTests {

    private static final String EMAIL = "usuario@email.com";
    private static final Principal PRINCIPAL = () -> EMAIL;
    private static final String BASE = "/api/v1/classrooms";
    private static final String VALID_BODY = "{\"title\": \"Turma A\", \"description\": \"Descrição\", \"period\": \"2026/01\"}";

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

        mockMvc.perform(post(BASE)
                .principal(PRINCIPAL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Turma A"))
                .andExpect(jsonPath("$.period").value("2026/01"));
    }

    @Test
    @DisplayName("POST /api/v1/classrooms com título vazio (400 Bad Request)")
    void shouldRejectBlankTitle() throws Exception {
        mockMvc.perform(post(BASE)
                .principal(PRINCIPAL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"\", \"period\": \"2026/01\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("POST /api/v1/classrooms com período fora do formato AAAA/SS (400 Bad Request)")
    void shouldRejectInvalidPeriod() throws Exception {
        mockMvc.perform(post(BASE)
                .principal(PRINCIPAL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"Turma A\", \"period\": \"2026/3\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("GET /api/v1/classrooms retorna as turmas do usuário (200 OK)")
    void shouldListClassrooms() throws Exception {
        when(service.findAll(eq(EMAIL), eq(""), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(classroom("Turma A"))));

        mockMvc.perform(get(BASE).principal(PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Turma A"));
    }

    @Test
    @DisplayName("GET /api/v1/classrooms?name= repassa o filtro de nome (200 OK)")
    void shouldListClassroomsByName() throws Exception {
        when(service.findAll(eq(EMAIL), eq("turma"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(classroom("Turma A"))));

        mockMvc.perform(get(BASE).param("name", "turma").principal(PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Turma A"));
    }

    @Test
    @DisplayName("GET /api/v1/classrooms/{id} devolve a turma com o código (200 OK)")
    void shouldFindClassroomById() throws Exception {
        ClassroomModel classroom = classroom("Turma A");
        when(service.findById(EMAIL, classroom.getId())).thenReturn(classroom);
        when(service.findJoinCode(EMAIL, classroom.getId())).thenReturn("ABC234");

        mockMvc.perform(get(BASE + "/" + classroom.getId()).principal(PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Turma A"))
                .andExpect(jsonPath("$.joinCode").value("ABC234"));
    }

    @Test
    @DisplayName("GET /api/v1/classrooms/{id} turma inexistente (404 Not Found)")
    void shouldReturnNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.findById(EMAIL, id)).thenThrow(new ClassroomNotFoundException());

        mockMvc.perform(get(BASE + "/" + id).principal(PRINCIPAL))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Turma não encontrada"));
    }

    @Test
    @DisplayName("GET /api/v1/classrooms/{id} sem acesso (403 Forbidden)")
    void shouldReturnForbidden() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.findById(EMAIL, id)).thenThrow(new ClassroomForbiddenException("Sem acesso a esta turma"));

        mockMvc.perform(get(BASE + "/" + id).principal(PRINCIPAL))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/classrooms/{id}/members lista os integrantes (200 OK)")
    void shouldListMembers() throws Exception {
        UUID id = UUID.randomUUID();
        ClassroomDTO.MemberInfo member = new ClassroomDTO.MemberInfo(UUID.randomUUID(), "Aluno",
                "aluno@email.com", UserRole.STUDENT, null);
        when(service.findMembers(eq(EMAIL), eq(id), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(member)));

        mockMvc.perform(get(BASE + "/" + id + "/members").principal(PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Aluno"))
                .andExpect(jsonPath("$.content[0].role").value("STUDENT"));
    }

    @Test
    @DisplayName("PUT /api/v1/classrooms/{id} altera a turma (200 OK)")
    void shouldUpdateClassroom() throws Exception {
        ClassroomModel classroom = classroom("Turma A");
        when(service.update(eq(EMAIL), eq(classroom.getId()), any(ClassroomDTO.Request.class)))
                .thenReturn(classroom);

        mockMvc.perform(put(BASE + "/" + classroom.getId())
                .principal(PRINCIPAL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Turma A"));
    }

    @Test
    @DisplayName("PUT /api/v1/classrooms/{id} turma arquivada (409 Conflict)")
    void shouldReturnConflictWhenArchived() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.update(eq(EMAIL), eq(id), any(ClassroomDTO.Request.class)))
                .thenThrow(new ClassroomArchivedException());

        mockMvc.perform(put(BASE + "/" + id)
                .principal(PRINCIPAL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("DELETE /api/v1/classrooms/{id} (204 No Content)")
    void shouldDeleteClassroom() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete(BASE + "/" + id).principal(PRINCIPAL))
                .andExpect(status().isNoContent());

        verify(service).delete(EMAIL, id);
    }

    @Test
    @DisplayName("PATCH /api/v1/classrooms/{id}/archive (204 No Content)")
    void shouldArchiveClassroom() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(patch(BASE + "/" + id + "/archive").principal(PRINCIPAL))
                .andExpect(status().isNoContent());

        verify(service).archive(EMAIL, id);
    }

    @Test
    @DisplayName("PATCH /api/v1/classrooms/{id}/unarchive (204 No Content)")
    void shouldUnarchiveClassroom() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(patch(BASE + "/" + id + "/unarchive").principal(PRINCIPAL))
                .andExpect(status().isNoContent());

        verify(service).unarchive(EMAIL, id);
    }

    @Test
    @DisplayName("POST /api/v1/classrooms/{id}/code gera novo código (201 Created)")
    void shouldGenerateCode() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.generateCode(EMAIL, id)).thenReturn(new ClassroomDTO.CodeResponse("ABC234", 900L));

        mockMvc.perform(post(BASE + "/" + id + "/code").principal(PRINCIPAL))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("ABC234"))
                .andExpect(jsonPath("$.expiresInSeconds").value(900));
    }

    @Test
    @DisplayName("POST /api/v1/classrooms/{id}/code com código ainda ativo (409 Conflict)")
    void shouldReturnConflictWhenCodeIsActive() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.generateCode(EMAIL, id)).thenThrow(new ClassroomCodeActiveException());

        mockMvc.perform(post(BASE + "/" + id + "/code").principal(PRINCIPAL))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /api/v1/classrooms/join com código válido (200 OK)")
    void shouldJoinClassroom() throws Exception {
        when(service.join(eq(EMAIL), any(ClassroomDTO.JoinRequest.class))).thenReturn(classroom("Turma A"));

        mockMvc.perform(post(BASE + "/join")
                .principal(PRINCIPAL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\": \"abc234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Turma A"));
    }

    @Test
    @DisplayName("POST /api/v1/classrooms/join com caractere proibido (400 Bad Request)")
    void shouldRejectInvalidCodeFormat() throws Exception {
        mockMvc.perform(post(BASE + "/join")
                .principal(PRINCIPAL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\": \"ABC10I\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("POST /api/v1/classrooms/join com código expirado (403 Forbidden)")
    void shouldRejectExpiredCode() throws Exception {
        when(service.join(eq(EMAIL), any(ClassroomDTO.JoinRequest.class)))
                .thenThrow(new ClassroomForbiddenException("Código inválido ou expirado"));

        mockMvc.perform(post(BASE + "/join")
                .principal(PRINCIPAL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\": \"ABC234\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Código inválido ou expirado"));
    }

    @Test
    @DisplayName("POST /api/v1/classrooms/join com aluno em outra turma (409 Conflict)")
    void shouldReturnConflictWhenStudentIsEnrolled() throws Exception {
        when(service.join(eq(EMAIL), any(ClassroomDTO.JoinRequest.class)))
                .thenThrow(new ClassroomStudentEnrolledException());

        mockMvc.perform(post(BASE + "/join")
                .principal(PRINCIPAL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\": \"ABC234\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("O aluno já está em outra turma"));
    }

    @Test
    @DisplayName("DELETE /api/v1/classrooms/{id}/leave (204 No Content)")
    void shouldLeaveClassroom() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete(BASE + "/" + id + "/leave").principal(PRINCIPAL))
                .andExpect(status().isNoContent());

        verify(service).leave(EMAIL, id);
    }

    @Test
    @DisplayName("POST /api/v1/classrooms/{id}/members/{userId} (204 No Content)")
    void shouldAddMember() throws Exception {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        mockMvc.perform(post(BASE + "/" + id + "/members/" + userId).principal(PRINCIPAL))
                .andExpect(status().isNoContent());

        verify(service).addMember(EMAIL, id, userId);
    }

    @Test
    @DisplayName("DELETE /api/v1/classrooms/{id}/members/{userId} (204 No Content)")
    void shouldRemoveMember() throws Exception {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        mockMvc.perform(delete(BASE + "/" + id + "/members/" + userId).principal(PRINCIPAL))
                .andExpect(status().isNoContent());

        verify(service).removeMember(EMAIL, id, userId);
    }

    @Test
    @DisplayName("DELETE /api/v1/classrooms/{id}/students limpa os alunos (204 No Content)")
    void shouldClearStudents() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete(BASE + "/" + id + "/students").principal(PRINCIPAL))
                .andExpect(status().isNoContent());

        verify(service).clearStudents(EMAIL, id);
    }

    private ClassroomModel classroom(String title) {
        return ClassroomModel.builder()
                .id(UUID.randomUUID())
                .title(title)
                .period("2026/01")
                .institution(InstitutionModel.builder().id(UUID.randomUUID()).build())
                .build();
    }
}
