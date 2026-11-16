package com.n0hana.echoes_server.classroom;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

import com.n0hana.echoes_server.classroom.exception.ClassroomArchivedException;
import com.n0hana.echoes_server.classroom.exception.ClassroomForbiddenException;
import com.n0hana.echoes_server.classroom.exception.ClassroomNotFoundException;
import com.n0hana.echoes_server.infra.security.JwtTokenService;
import com.n0hana.echoes_server.institution.InstitutionModel;
import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.model.Teacher;

@WebMvcTest(ClassroomMaterialController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class ClassroomMaterialControllerTests {

    private static final String EMAIL = "usuario@email.com";
    private static final Principal PRINCIPAL = () -> EMAIL;
    private static final UUID CLASSROOM_ID = UUID.randomUUID();
    private static final UUID MATERIAL_ID = UUID.randomUUID();
    private static final String BASE = "/api/v1/classrooms/" + CLASSROOM_ID + "/materials";
    private static final String VALID_BODY = "{\"title\": \"Aula 1\", \"topic\": \"Cardiologia\"}";

    @TempDir
    Path tempDir;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClassroomMaterialService service;

    @MockitoBean
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private UserRepository userRepository;

    // postar

    @Test
    @DisplayName("POST /api/v1/classrooms/{id}/materials com dados válidos (201 Created)")
    void shouldCreateMaterial() throws Exception {
        when(service.create(eq(EMAIL), eq(CLASSROOM_ID), any(ClassroomMaterialDTO.Request.class),
                any(MultipartFile.class))).thenReturn(material());

        mockMvc.perform(multipart(BASE).file(dtoPart(VALID_BODY)).file(filePart()).principal(PRINCIPAL))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Aula 1"))
                .andExpect(jsonPath("$.topic").value("Cardiologia"))
                .andExpect(jsonPath("$.classroomId").value(CLASSROOM_ID.toString()));
    }

    @Test
    @DisplayName("POST /api/v1/classrooms/{id}/materials com título vazio (400 Bad Request)")
    void shouldRejectBlankTitle() throws Exception {
        mockMvc.perform(multipart(BASE)
                .file(dtoPart("{\"title\": \"\", \"topic\": \"Cardiologia\"}"))
                .file(filePart())
                .principal(PRINCIPAL))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("POST /api/v1/classrooms/{id}/materials sem tópico (400 Bad Request)")
    void shouldRejectBlankTopic() throws Exception {
        mockMvc.perform(multipart(BASE)
                .file(dtoPart("{\"title\": \"Aula 1\", \"topic\": \"\"}"))
                .file(filePart())
                .principal(PRINCIPAL))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("POST /api/v1/classrooms/{id}/materials sem permissão (403 Forbidden)")
    void shouldReturnForbiddenOnCreate() throws Exception {
        when(service.create(eq(EMAIL), eq(CLASSROOM_ID), any(ClassroomMaterialDTO.Request.class),
                any(MultipartFile.class))).thenThrow(new ClassroomForbiddenException("Sem permissão"));

        mockMvc.perform(multipart(BASE).file(dtoPart(VALID_BODY)).file(filePart()).principal(PRINCIPAL))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Sem permissão"));
    }

    @Test
    @DisplayName("POST /api/v1/classrooms/{id}/materials em turma arquivada (409 Conflict)")
    void shouldReturnConflictOnArchivedClassroom() throws Exception {
        when(service.create(eq(EMAIL), eq(CLASSROOM_ID), any(ClassroomMaterialDTO.Request.class),
                any(MultipartFile.class))).thenThrow(new ClassroomArchivedException("Turma arquivada"));

        mockMvc.perform(multipart(BASE).file(dtoPart(VALID_BODY)).file(filePart()).principal(PRINCIPAL))
                .andExpect(status().isConflict());
    }

    // listar

    @Test
    @DisplayName("GET /api/v1/classrooms/{id}/materials retorna o material da turma (200 OK)")
    void shouldListMaterials() throws Exception {
        when(service.findAll(eq(EMAIL), eq(CLASSROOM_ID), eq(""), eq(""), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(info())));

        mockMvc.perform(get(BASE).principal(PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Aula 1"))
                .andExpect(jsonPath("$.content[0].topic").value("Cardiologia"));
    }

    @Test
    @DisplayName("GET /api/v1/classrooms/{id}/materials?title=&topic= repassa os filtros (200 OK)")
    void shouldListMaterialsWithFilters() throws Exception {
        when(service.findAll(eq(EMAIL), eq(CLASSROOM_ID), eq("aula"), eq("Cardio"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(info())));

        mockMvc.perform(get(BASE).param("title", "aula").param("topic", "Cardio").principal(PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Aula 1"));
    }

    @Test
    @DisplayName("GET /api/v1/classrooms/{id}/materials sem acesso à turma (403 Forbidden)")
    void shouldReturnForbiddenOnList() throws Exception {
        when(service.findAll(eq(EMAIL), eq(CLASSROOM_ID), eq(""), eq(""), any(Pageable.class)))
                .thenThrow(new ClassroomForbiddenException("Sem acesso a esta turma"));

        mockMvc.perform(get(BASE).principal(PRINCIPAL))
                .andExpect(status().isForbidden());
    }

    // baixar

    @Test
    @DisplayName("GET /api/v1/classrooms/{id}/materials/{materialId}/download entrega o arquivo (200 OK)")
    void shouldDownloadMaterial() throws Exception {
        Path file = Files.writeString(tempDir.resolve("arquivo.pdf"), "conteudo");
        when(service.download(EMAIL, CLASSROOM_ID, MATERIAL_ID))
                .thenReturn(new ClassroomMaterialDTO.Download(file, "Aula 1.pdf"));

        mockMvc.perform(get(BASE + "/" + MATERIAL_ID + "/download").principal(PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(header().exists("Content-Disposition"))
                .andExpect(content().bytes("conteudo".getBytes()));
    }

    @Test
    @DisplayName("GET /api/v1/classrooms/{id}/materials/{materialId}/download inexistente (404 Not Found)")
    void shouldReturnNotFoundOnDownload() throws Exception {
        when(service.download(EMAIL, CLASSROOM_ID, MATERIAL_ID))
                .thenThrow(new ClassroomNotFoundException("Material não encontrado"));

        mockMvc.perform(get(BASE + "/" + MATERIAL_ID + "/download").principal(PRINCIPAL))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Material não encontrado"));
    }

    // editar

    @Test
    @DisplayName("PUT /api/v1/classrooms/{id}/materials/{materialId} com dados válidos (200 OK)")
    void shouldUpdateMaterial() throws Exception {
        when(service.update(eq(EMAIL), eq(CLASSROOM_ID), eq(MATERIAL_ID), any(ClassroomMaterialDTO.Request.class)))
                .thenReturn(material());

        mockMvc.perform(put(BASE + "/" + MATERIAL_ID)
                .principal(PRINCIPAL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Aula 1"));
    }

    @Test
    @DisplayName("PUT /api/v1/classrooms/{id}/materials/{materialId} com título vazio (400 Bad Request)")
    void shouldRejectBlankTitleOnUpdate() throws Exception {
        mockMvc.perform(put(BASE + "/" + MATERIAL_ID)
                .principal(PRINCIPAL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"\", \"topic\": \"Cardiologia\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("PUT /api/v1/classrooms/{id}/materials/{materialId} de outro professor (403 Forbidden)")
    void shouldReturnForbiddenOnUpdate() throws Exception {
        when(service.update(eq(EMAIL), eq(CLASSROOM_ID), eq(MATERIAL_ID), any(ClassroomMaterialDTO.Request.class)))
                .thenThrow(new ClassroomForbiddenException("Você só pode alterar as suas próprias postagens"));

        mockMvc.perform(put(BASE + "/" + MATERIAL_ID)
                .principal(PRINCIPAL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY))
                .andExpect(status().isForbidden());
    }

    // excluir

    @Test
    @DisplayName("DELETE /api/v1/classrooms/{id}/materials/{materialId} (204 No Content)")
    void shouldDeleteMaterial() throws Exception {
        mockMvc.perform(delete(BASE + "/" + MATERIAL_ID).principal(PRINCIPAL))
                .andExpect(status().isNoContent());

        verify(service).delete(EMAIL, CLASSROOM_ID, MATERIAL_ID);
    }

    @Test
    @DisplayName("DELETE /api/v1/classrooms/{id}/materials/{materialId} inexistente (404 Not Found)")
    void shouldReturnNotFoundOnDelete() throws Exception {
        when(service.delete(EMAIL, CLASSROOM_ID, MATERIAL_ID))
                .thenThrow(new ClassroomNotFoundException("Material não encontrado"));

        mockMvc.perform(delete(BASE + "/" + MATERIAL_ID).principal(PRINCIPAL))
                .andExpect(status().isNotFound());
    }

    // apoio

    private MockMultipartFile dtoPart(String json) {
        return new MockMultipartFile("dto", "", MediaType.APPLICATION_JSON_VALUE, json.getBytes());
    }

    private MockMultipartFile filePart() {
        return new MockMultipartFile("file", "aula.pdf", "application/pdf", "conteudo".getBytes());
    }

    private ClassroomMaterial material() {
        Teacher author = new Teacher();
        author.setId(UUID.randomUUID());

        ClassroomModel classroom = ClassroomModel.builder()
                .id(CLASSROOM_ID)
                .title("Turma")
                .period("2026/01")
                .institution(InstitutionModel.builder().id(UUID.randomUUID()).build())
                .build();

        return ClassroomMaterial.builder()
                .id(MATERIAL_ID)
                .classroom(classroom)
                .author(author)
                .title("Aula 1")
                .topic("Cardiologia")
                .fileName("uuid_Aula_1.pdf")
                .createdAt(LocalDateTime.now())
                .build();
    }

    private ClassroomMaterialDTO.Info info() {
        return ClassroomMaterialDTO.Info.from(material());
    }
}
