package com.n0hana.echoes_server.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import com.n0hana.echoes_server.auscultation.AuscultationPointModel;
import com.n0hana.echoes_server.device.DeviceModel;
import com.n0hana.echoes_server.scenario.ScenarioDTO.ScenarioInfo;
import com.n0hana.echoes_server.scenario.ScenarioDTO.ScenarioRegister;
import com.n0hana.echoes_server.scenario.ScenarioDTO.ScenarioUpdate;

@ExtendWith(MockitoExtension.class)
class ScenarioControllerCoverageTests {

    @Mock
    private ScenarioService scenarioService;

    @InjectMocks
    private ScenarioController controller;

    private ScenarioModel model(UUID pointId) {
        return ScenarioModel.builder().id(UUID.randomUUID()).name("Sopro").description("Descrição")
                .audioUrl("sopro.mp3")
                .auscultationPoint(pointId == null ? null : AuscultationPointModel.builder().id(pointId).build())
                .build();
    }

    @Test
    @DisplayName("POST cria cenário e responde 201 com o DTO")
    void shouldCreate() {
        UUID pointId = UUID.randomUUID();
        ScenarioModel created = model(pointId);
        MockMultipartFile file = new MockMultipartFile("file", "a.mp3", "audio/mpeg", new byte[] { 1 });
        when(scenarioService.create(any(ScenarioModel.class), any())).thenReturn(created);

        ResponseEntity<ScenarioInfo> response = controller
                .newScenario(new ScenarioRegister("Sopro", "Descrição", pointId), file);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(created.getId(), response.getBody().id());
        assertEquals(pointId, response.getBody().pointId());
    }

    @Test
    @DisplayName("GET por ponto devolve a lista mapeada")
    void shouldFindByPoint() {
        UUID pointId = UUID.randomUUID();
        DeviceModel device = new DeviceModel();
        when(scenarioService.findScenariosByPoint(pointId, 0, 10, device)).thenReturn(List.of(model(pointId)));

        ResponseEntity<List<ScenarioInfo>> response = controller.findAllScenariosByPoint(0, 10, pointId, device);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals("Sopro", response.getBody().get(0).name());
    }

    @Test
    @DisplayName("GET /me devolve os cenários do dispositivo")
    void shouldFindMine() {
        DeviceModel device = new DeviceModel();
        when(scenarioService.findScenariosByDevice(0, 10, device)).thenReturn(List.of(model(null), model(null)));

        ResponseEntity<List<ScenarioInfo>> response = controller.findMyScenarios(0, 10, device);

        assertEquals(2, response.getBody().size());
        assertNull(response.getBody().get(0).pointId());
    }

    @Test
    @DisplayName("GET /{id}/audio devolve o arquivo com content-type deduzido da extensão")
    void shouldDownloadAudio(@TempDir Path dir) throws Exception {
        UUID id = UUID.randomUUID();
        DeviceModel device = new DeviceModel();
        Path mp3 = Files.createFile(dir.resolve("a.mp3"));
        when(scenarioService.findAudioPath(id, device)).thenReturn(mp3);

        ResponseEntity<Resource> response = controller.downloadAudio(id, device);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(MediaType.parseMediaType("audio/mpeg"), response.getHeaders().getContentType());
        assertTrue(response.getBody().exists());
    }

    @Test
    @DisplayName("GET /{id}/audio usa octet-stream quando a extensão é desconhecida")
    void shouldDownloadAudioUnknownType(@TempDir Path dir) throws Exception {
        UUID id = UUID.randomUUID();
        Path file = Files.createFile(dir.resolve("audio.desconhecida123"));
        when(scenarioService.findAudioPath(id, null)).thenReturn(file);

        ResponseEntity<Resource> response = controller.downloadAudio(id, null);

        assertEquals(MediaType.APPLICATION_OCTET_STREAM, response.getHeaders().getContentType());
    }

    @Test
    @DisplayName("Busca por nome usa o serviço do dispositivo quando autenticado como dispositivo")
    void shouldSearchForDevice() {
        DeviceModel device = new DeviceModel();
        when(scenarioService.findScenariosByNameForDevice(0, 10, "So", device)).thenReturn(List.of(model(null)));

        ResponseEntity<List<ScenarioInfo>> response = controller.findAllScenariosByName(0, 10, "So", device);

        assertEquals(1, response.getBody().size());
        verify(scenarioService, never()).findScenariosByName(0, 10, "So");
    }

    @Test
    @DisplayName("Busca por nome usa o serviço geral quando não há dispositivo")
    void shouldSearchWithoutDevice() {
        when(scenarioService.findScenariosByName(0, 10, "So")).thenReturn(List.of(model(null)));

        ResponseEntity<List<ScenarioInfo>> response = controller.findAllScenariosByName(0, 10, "So", null);

        assertEquals(1, response.getBody().size());
        verify(scenarioService, never()).findScenariosByNameForDevice(any(Integer.class), any(Integer.class), any(),
                any());
    }

    @Test
    @DisplayName("GET /{id} devolve o cenário")
    void shouldFindById() {
        ScenarioModel m = model(UUID.randomUUID());
        when(scenarioService.findScenarioById(m.getId(), null)).thenReturn(m);

        ResponseEntity<ScenarioInfo> response = controller.findScenarioById(m.getId(), null);

        assertEquals(m.getId(), response.getBody().id());
        assertEquals("sopro.mp3", response.getBody().audioPath());
    }

    @Test
    @DisplayName("PATCH atualiza e responde 200")
    void shouldUpdate() {
        ScenarioModel m = model(null);
        when(scenarioService.updateScenario(any(UUID.class), any(ScenarioModel.class))).thenReturn(m);

        ResponseEntity<ScenarioInfo> response = controller.updateScenario(m.getId(),
                new ScenarioUpdate("Sopro", "Descrição"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Sopro", response.getBody().name());
    }

    @Test
    @DisplayName("PATCH upload responde 204")
    void shouldReupload() {
        UUID id = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("file", "a.mp3", "audio/mpeg", new byte[] { 1 });

        ResponseEntity<Void> response = controller.reuploadFile(file, id);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(scenarioService).reuploadFile(id, file);
    }

    @Test
    @DisplayName("Desativar e ativar devolvem o cenário atualizado")
    void shouldDeactivateAndActivate() {
        ScenarioModel m = model(null);
        when(scenarioService.deactivateScenario(m.getId())).thenReturn(m);
        when(scenarioService.activateScenario(m.getId())).thenReturn(m);

        assertEquals(HttpStatus.OK, controller.deactivateScenario(m.getId()).getStatusCode());
        ResponseEntity<ScenarioInfo> activated = controller.activateScenario(m.getId());
        assertNotNull(activated.getBody());
        assertEquals(m.getId(), activated.getBody().id());
    }

    @Test
    @DisplayName("DELETE responde 204")
    void shouldDelete() {
        UUID id = UUID.randomUUID();

        ResponseEntity<Void> response = controller.deleteScenario(id);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(scenarioService).deleteScenario(id);
    }
}
