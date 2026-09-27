package com.n0hana.echoes_server.scenario;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.n0hana.echoes_server.auscultation.AuscultationPointModel;
import com.n0hana.echoes_server.infra.security.JwtTokenService;
import com.n0hana.echoes_server.user.UserRepository;

@WebMvcTest(ScenarioController.class)
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser
@ActiveProfiles("test")
public class ScenarioControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ScenarioService service;

    @MockitoBean
    private JwtTokenService jwtTokenService;
    @MockitoBean
    private UserRepository userRepository;

    // TODO arrumar teste para arquivo multipart
    @Test
    @DisplayName("POST /api/v1/scenarios com dados válidos (201 Created)")
    void shouldCreateScenario() throws Exception {
        // Arrange
        ScenarioModel model = this.createTestModel(UUID.randomUUID());

        when(service.create(any(ScenarioModel.class), any(MockMultipartFile.class)))
                .thenReturn(model);

        // Act e Assert
        String payload = """
                {
                    "name": "Cenário de Teste",
                    "description": "Descrição para o Cenário de Teste",
                    "pointId": "91748f73-53c0-4c63-934a-28b09c996314"
                }
                """;

        MockMultipartFile filePart = new MockMultipartFile(
                "file",
                "audio.mp3",
                MediaType.MULTIPART_FORM_DATA_VALUE,
                "conteudo audio".getBytes());

        MockMultipartFile dtoPart = new MockMultipartFile(
                "dto", "",
                MediaType.APPLICATION_JSON_VALUE,
                payload.getBytes());

        mockMvc.perform(multipart("/api/v1/scenarios")
                .file(dtoPart)
                .file(filePart))
                .andExpect(status().isCreated());
    }

    private ScenarioModel createTestModel(UUID id) {
        AuscultationPointModel point = AuscultationPointModel.builder()
                .id(UUID.randomUUID())
                .build();

        ScenarioModel model = ScenarioModel.builder()
                .id(id)
                .name("Cenário Teste")
                .description("Cenário para testes e demais finalidades")
                .auscultationPoint(point)
                .audioUrl("arquivo.txt")
                .build();

        return model;
    }
}
