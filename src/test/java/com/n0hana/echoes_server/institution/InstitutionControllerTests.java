package com.n0hana.echoes_server.institution;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.n0hana.echoes_server.institution.exception.InstitutionNotFoundException;
import com.n0hana.echoes_server.infra.security.JwtTokenService;
import com.n0hana.echoes_server.user.UserRepository;
@WebMvcTest(InstitutionController.class)
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser
@ActiveProfiles("test")
public class InstitutionControllerTests {

    private static final String CNPJ_VALIDO = "19.131.243/0001-97";
    private static final String CNPJ_VALIDO_SEM_MASCARA = "19131243000197";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InstitutionService service;
    

    @MockitoBean private JwtTokenService jwtTokenService;
    @MockitoBean private UserRepository userRepository;

    @Test
    @DisplayName("POST /api/v1/institutions com dados válidos (201 Created)")
    void shouldCreateInstitution() throws Exception {
        UUID uuid = UUID.randomUUID();
        InstitutionModel model = createTestModel(uuid);

        when(service.create(any(InstitutionModel.class))).thenReturn(model);

        String payload = """
                {
                    "name": "Instituição Teste",
                    "acronym": "IT",
                    "cnpj": "%s",
                    "email": "contato@teste.com",
                    "phone": "11901234567",
                    "address": "Rua nova"
                }
                """.formatted(CNPJ_VALIDO);

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

        mockMvc.perform(post("/api/v1/institutions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/institutions com cnpj já cadastrado (400 Bad Request)")
    void shouldThrowDuplicationKeyExceptionInCreateInstitution() throws Exception {
        String payload = """
                {
                    "name": "Instituição Teste",
                    "acronym": "IT",
                    "cnpj": "%s",
                    "email": "contato@teste.com",
                    "phone": "11901234567",
                    "address": "Rua nova"
                }
                """.formatted(CNPJ_VALIDO);

        when(service.create(any(InstitutionModel.class)))
                .thenThrow(new DuplicateKeyException("Instituição já cadastrada com este CNPJ ou E-mail"));

        mockMvc.perform(post("/api/v1/institutions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/institutions retorna envelope paginado (200 OK)")
    void shouldFindAllInstitutions() throws Exception {
        UUID uuid = UUID.randomUUID();
        InstitutionModel model = createTestModel(uuid);

        Page<InstitutionModel> page = new PageImpl<>(
                List.of(model),
                PageRequest.of(0, 10, Sort.by("name")),
                1);

        when(service.findAll(any(), any(Integer.class), any(Integer.class), any(String.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/institutions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].id").value(uuid.toString()))
                .andExpect(jsonPath("$.content[0].name").value(model.getName()))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.number").value(0));
    }

    @Test
    @DisplayName("GET /api/v1/institutions filtrado por nome (200 OK paginado)")
    void shouldFindInstitutionsByName() throws Exception {
        UUID uuid = UUID.randomUUID();
        String searchName = "Teste";
        InstitutionModel model = createTestModel(uuid);

        Page<InstitutionModel> page = new PageImpl<>(
                List.of(model),
                PageRequest.of(0, 10, Sort.by("name")),
                1);

        when(service.findAll(eq(searchName), any(Integer.class), any(Integer.class), any(String.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/institutions").param("name", searchName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].name").value(model.getName()))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/institutions sem resultados (200 OK vazio paginado)")
    void shouldNotFindInstitutionsByName() throws Exception {
        Page<InstitutionModel> empty = Page.empty();

        when(service.findAll(any(), any(Integer.class), any(Integer.class), any(String.class)))
                .thenReturn(empty);

        mockMvc.perform(get("/api/v1/institutions").param("name", "Inexistente"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("GET /api/v1/institutions/{id} instituição encontrada (200 OK)")
    void shouldFindInstitutionById() throws Exception {
        UUID uuid = UUID.randomUUID();
        InstitutionModel model = createTestModel(uuid);

        when(service.findById(uuid)).thenReturn(model);

        mockMvc.perform(get("/api/v1/institutions/{id}", uuid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(uuid.toString()))
                .andExpect(jsonPath("$.name").value(model.getName()));
    }

    @Test
    @DisplayName("GET /api/v1/institutions/{id} não encontrada (404 Not Found)")
    void shouldNotFindInstitutionById() throws Exception {
        UUID uuid = UUID.randomUUID();

        when(service.findById(uuid))
                .thenThrow(new InstitutionNotFoundException("Instituição não encontrada com ID: " + uuid));

        mockMvc.perform(get("/api/v1/institutions/{id}", uuid))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("message").value("Instituição não encontrada com ID: " + uuid));
    }

    @Test
    @DisplayName("PUT /api/v1/institutions/{id} atualiza os dados (200 OK)")
    void shouldUpdateInstitutionById() throws Exception {
        UUID uuid = UUID.randomUUID();
        InstitutionModel model = createTestModel(uuid);
        model.setName("Instituição Atualizada");

        when(service.update(eq(uuid), any(InstitutionModel.class))).thenReturn(model);

        String payload = """
                {
                    "name": "Instituição Atualizada",
                    "acronym": "IT",
                    "cnpj": "%s",
                    "email": "contato@teste.com",
                    "phone": "11901234567",
                    "address": "Rua nova"
                }
                """.formatted(CNPJ_VALIDO);

        mockMvc.perform(put("/api/v1/institutions/{id}", uuid)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Instituição Atualizada"));
    }

    @Test
    @DisplayName("PATCH /api/v1/institutions/{id}/toggle-status (204 No Content)")
    void shouldUpdateInstitutionStatus() throws Exception {
        UUID uuid = UUID.randomUUID();

        mockMvc.perform(patch("/api/v1/institutions/{id}/toggle-status", uuid))
                .andExpect(status().isNoContent());

        verify(service).toggleStatus(uuid);
    }

    @Test
    @DisplayName("DELETE /api/v1/institutions/{id} (204 No Content)")
    void shouldUpdateInstitutionDeleteStatus() throws Exception {
        UUID uuid = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/institutions/{id}", uuid))
                .andExpect(status().isNoContent());

        verify(service).delete(uuid);
    }

    private InstitutionModel createTestModel(UUID id) {
        return InstitutionModel.builder()
                .id(id)
                .name("Instituição Teste")
                .acronym("IT")
                .cnpj(CNPJ_VALIDO_SEM_MASCARA)
                .email("contato@teste.com")
                .phone("11999999999")
                .address("Rua Teste, 123")
                .active(true)
                .deleted(false)
                .build();
    }
}
