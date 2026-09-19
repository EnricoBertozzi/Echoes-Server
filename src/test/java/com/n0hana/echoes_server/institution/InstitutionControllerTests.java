package com.n0hana.echoes_server.institution;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.n0hana.echoes_server.institution.exception.InstitutionNotFoundException;

import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InstitutionController.class)
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser
public class InstitutionControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InstitutionService service;

    @Test
    @DisplayName("POST /api/v1/institutions com dados válidos (201 Created)")
    void shouldCreateInstitution() throws Exception {
        // Arrange
        UUID uuid = UUID.randomUUID();
        InstitutionModel model = createTestModel(uuid);

        when(service.create(any(InstitutionModel.class))).thenReturn(model);

        String payload = """
                {
                    "name": "Instituição Teste",
                    "acronym": "IT",
                    "cnpj": "00.000.000/0001-91",
                    "email": "contato@teste.com",
                    "phone": "11901234567",
                    "address": "Rua nova"
                }
                """;

        // Act e Assert
        mockMvc.perform(post("/api/v1/institutions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(uuid.toString()))
                .andExpect(jsonPath("$.name").value("Instituição Teste"))
                .andExpect(jsonPath("$.acronym").value("IT"));
    }

    @Test
    @DisplayName("POST /api/v1/institutions com dados inválidos (400 Bad Request)")
    void shouldNotCreateInstitutionWithInvalidFields() throws Exception {
        // Arrange
        String payload = """
                {
                    "name": "",
                    "acronym": "",
                    "cnpj": "123",
                    "email": "invalido",
                    "phone": "11901234567",
                    "address": "Rua nova"
                }
                """;
        // Act e Assert
        mockMvc.perform(post("/api/v1/institutions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/institutions com cnpj já cadastrado (400 Bad Request)")
    void shouldThrowDuplicationKeyExceptionInCreateInstitution() throws Exception {
        // Arrange
        String payload = """
                {
                    "name": "Instituição Teste",
                    "acronym": "IT",
                    "cnpj": "00.000.000/0001-91",
                    "email": "contato@teste.com",
                    "phone": "11901234567",
                    "address": "Rua nova"
                }
                """;

        when(service.create(any(InstitutionModel.class)))
                .thenThrow(new DuplicateKeyException("Instituição já cadastrada com este CNPJ ou E-mail"));

        // Act e Assert
        mockMvc.perform(post("/api/v1/institutions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isBadRequest());

    }

    @Test
    @DisplayName("GET /api/v1/institutions com lista de todas as instituições (200 OK)")
    void shouldFindAllInstitutions() throws Exception {
        // Arrange
        UUID uuid = UUID.randomUUID();
        InstitutionModel model = createTestModel(uuid);

        when(service.findAll(any(), any(Integer.class), any(Integer.class), any(String.class)))
                .thenReturn(List.of(model));

        // Act e Assert
        mockMvc.perform(get("/api/v1/institutions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].name").value(model.getName()));
    }

    @Test
    @DisplayName("GET /api/v1/institutions lista com as instituições filtradas por nome (200 OK)")
    void shouldFindInstitutionsByName() throws Exception {
        // Arrange
        UUID uuid = UUID.randomUUID();
        String searchName = "Teste";
        InstitutionModel model = createTestModel(uuid);

        when(service.findAll(eq(searchName), any(Integer.class), any(Integer.class), any(String.class)))
                .thenReturn(List.of(model));

        // Act e Assert
        mockMvc.perform(get("/api/v1/institutions")
                .param("name", searchName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].name").value(model.getName()));
    }

    @Test
    @DisplayName("GET /api/v1/institutions não encontra nenhuma instituição pelo nome (200 OK com lista vazia)")
    void shouldNotFindInstitutionsByName() throws Exception {
        // Arrange
        when(service.findAll(any(), any(Integer.class), any(Integer.class), any(String.class)))
                .thenReturn(List.of());

        // Act e Assert
        mockMvc.perform(get("/api/v1/institutions")
                .param("name", "Inexistente"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("GET /api/v1/institutions/{id} instituição encontrada pelo id (200 OK)")
    void shouldFindInstitutionById() throws Exception {
        // Arrange
        UUID uuid = UUID.randomUUID();
        InstitutionModel model = createTestModel(uuid);

        when(service.findById(uuid)).thenReturn(model);

        // Act e Assert
        mockMvc.perform(get("/api/v1/institutions/{id}", uuid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(uuid.toString()))
                .andExpect(jsonPath("$.name").value(model.getName()));
    }

    @Test
    @DisplayName("GET /api/v1/institutions/{id} não encontrada instituição pelo id (404 Not Found)")
    void shouldNotFindInstitutionById() throws Exception {
        // Arrange
        UUID uuid = UUID.randomUUID();

        when(service.findById(uuid))
                .thenThrow(new InstitutionNotFoundException("Instituição não encontrada com ID: " + uuid));

        // Act e Assert
        mockMvc.perform(get("/api/v1/institutions/{id}", uuid))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("message").value("Instituição não encontrada com ID: " + uuid));
    }

    @Test
    @DisplayName("PUT /api/v1/institutions/{id} atualiza os dados da instituição (200 OK)")
    void shouldUpdateInstitutionById() throws Exception {
        // Arrange
        UUID uuid = UUID.randomUUID();
        InstitutionModel model = createTestModel(uuid);
        model.setName("Instituição Atualizada");

        when(service.update(eq(uuid), any(InstitutionModel.class))).thenReturn(model);

        // Act e Assert
        String payload = """
                {
                    "name": "Instituição Atualizada",
                    "acronym": "IT",
                    "cnpj": "00.000.000/0001-91",
                    "email": "contato@teste.com",
                    "phone": "11901234567",
                    "address": "Rua nova"
                }
                """;

        mockMvc.perform(put("/api/v1/institutions/{id}", uuid)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Instituição Atualizada"));
    }

    @Test
    @DisplayName("PATCH /api/v1/institutions/{id}/toggle-status atualiza o estado da instituição (204 No Content)")
    void shouldUpdateInstitutionStatus() throws Exception {
        // Arrange
        UUID uuid = UUID.randomUUID();

        // Act e Assert
        mockMvc.perform(patch("/api/v1/institutions/{id}/toggle-status", uuid))
                .andExpect(status().isNoContent());

        verify(service).toggleStatus(uuid);
    }

    @Test
    @DisplayName("DELETE /api/v1/institutions/{id} desativa a instituição (204 No Content)")
    void shouldUpdateInstitutionDeleteStatus() throws Exception {
        // Arrange
        UUID uuid = UUID.randomUUID();

        // Act e Assert
        mockMvc.perform(delete("/api/v1/institutions/{id}", uuid))
                .andExpect(status().isNoContent());

        verify(service).delete(uuid);
    }

    private InstitutionModel createTestModel(UUID id) {
        InstitutionModel model = InstitutionModel.builder()
                .id(id)
                .name("Instituição Teste")
                .acronym("IT")
                .cnpj("12.345.678/0001-90")
                .email("contato@teste.com")
                .phone("11999999999")
                .address("Rua Teste, 123")
                .active(true)
                .deleted(false)
                .build();
        return model;
    }
}
