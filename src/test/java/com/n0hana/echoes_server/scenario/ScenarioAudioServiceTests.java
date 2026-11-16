package com.n0hana.echoes_server.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.n0hana.echoes_server.auscultation.AuscultationPointModel;
import com.n0hana.echoes_server.auscultation.AuscultationPointService;
import com.n0hana.echoes_server.device.DeviceModel;
import com.n0hana.echoes_server.infra.file.FileStorageService;
import com.n0hana.echoes_server.infra.file.exception.StorageFileNotFoundException;
import com.n0hana.echoes_server.scenario.exception.ScenarioNotFoundException;

@ExtendWith(MockitoExtension.class)
class ScenarioAudioServiceTests {

  @Mock
  private ScenarioRepository scenarioRepository;

  @Mock
  private FileStorageService fileService;

  @Mock
  private AuscultationPointService auscultationPointService;

  @InjectMocks
  private ScenarioService service;

  @TempDir
  Path dir;

  private ScenarioModel scenario(UUID pointId) {
    AuscultationPointModel point = AuscultationPointModel.builder().id(pointId).build();
    return ScenarioModel.builder().id(UUID.randomUUID()).name("Sopro").audioUrl("sopro.mp3")
        .auscultationPoint(point).build();
  }

  @Test
  @DisplayName("Devolve o caminho do áudio quando o cenário é do animal do dispositivo")
  void returnsAudioPath() throws Exception {
    ScenarioModel scenario = scenario(UUID.randomUUID());
    DeviceModel device = DeviceModel.builder().id(UUID.randomUUID()).build();
    Path file = Files.createFile(dir.resolve("sopro.mp3"));
    when(scenarioRepository.findById(scenario.getId())).thenReturn(Optional.of(scenario));
    when(auscultationPointService.findPointById(scenario.getAuscultationPoint().getId(), device))
        .thenReturn(scenario.getAuscultationPoint());
    when(fileService.load("sopro.mp3")).thenReturn(file);

    assertEquals(file, service.findAudioPath(scenario.getId(), device));
  }

  @Test
  @DisplayName("Arquivo de áudio ausente no disco responde não encontrado")
  void missingFileIsNotFound() {
    ScenarioModel scenario = scenario(UUID.randomUUID());
    DeviceModel device = DeviceModel.builder().id(UUID.randomUUID()).build();
    when(scenarioRepository.findById(scenario.getId())).thenReturn(Optional.of(scenario));
    when(auscultationPointService.findPointById(scenario.getAuscultationPoint().getId(), device))
        .thenReturn(scenario.getAuscultationPoint());
    when(fileService.load("sopro.mp3")).thenReturn(dir.resolve("nao-existe.mp3"));

    assertThrows(StorageFileNotFoundException.class, () -> service.findAudioPath(scenario.getId(), device));
  }

  @Test
  @DisplayName("Cenário que não existe responde não encontrado")
  void missingScenarioIsNotFound() {
    UUID id = UUID.randomUUID();
    when(scenarioRepository.findById(id)).thenReturn(Optional.empty());

    assertThrows(ScenarioNotFoundException.class,
        () -> service.findAudioPath(id, DeviceModel.builder().id(UUID.randomUUID()).build()));
  }

  @Test
  @DisplayName("Cenário sem ponto não é entregue ao dispositivo")
  void scenarioWithoutPointIsDenied() {
    ScenarioModel scenario = ScenarioModel.builder().id(UUID.randomUUID()).audioUrl("sopro.mp3").build();
    when(scenarioRepository.findById(scenario.getId())).thenReturn(Optional.of(scenario));

    assertThrows(AccessDeniedException.class, () -> service.findAudioPath(scenario.getId(),
        DeviceModel.builder().id(UUID.randomUUID()).build()));
  }
}
