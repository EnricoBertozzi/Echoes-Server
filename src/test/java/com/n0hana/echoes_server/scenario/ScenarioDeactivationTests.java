package com.n0hana.echoes_server.scenario;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.n0hana.echoes_server.auscultation.AuscultationPointService;
import com.n0hana.echoes_server.device.DeviceScenarioRepository;
import com.n0hana.echoes_server.infra.file.FileStorageService;
import com.n0hana.echoes_server.scenario.exception.ScenarioInUseException;

@ExtendWith(MockitoExtension.class)
class ScenarioDeactivationTests {

    @Mock
    private ScenarioRepository scenarioRepository;

    @Mock
    private FileStorageService fileService;

    @Mock
    private AuscultationPointService auscultationPointService;

    @Mock
    private DeviceScenarioRepository deviceScenarioRepository;

    @InjectMocks
    private ScenarioService service;

    private ScenarioModel scenario;

    @BeforeEach
    void setUp() {
        scenario = ScenarioModel.builder().id(UUID.randomUUID()).name("Sopro").description("Sopro mitral")
                .audioUrl("sopro.mp3").build();
    }

    @Test
    @DisplayName("Cenário configurado em dispositivo não pode ser removido e o áudio é mantido")
    void shouldNotDeleteScenarioInUse() {
        when(scenarioRepository.findById(scenario.getId())).thenReturn(Optional.of(scenario));
        when(deviceScenarioRepository.existsByScenarioId(scenario.getId())).thenReturn(true);

        assertThrows(ScenarioInUseException.class, () -> service.deleteScenario(scenario.getId()));
        verify(scenarioRepository, never()).delete(any());
        verify(fileService, never()).delete(any());
    }

    @Test
    @DisplayName("Cenário sem dispositivos associados é removido")
    void shouldDeleteScenarioWithoutDevices() {
        when(scenarioRepository.findById(scenario.getId())).thenReturn(Optional.of(scenario));

        service.deleteScenario(scenario.getId());

        verify(scenarioRepository).delete(scenario);
    }

    @Test
    @DisplayName("Desativa e reativa um cenário")
    void shouldDeactivateAndActivateScenario() {
        when(scenarioRepository.findById(scenario.getId())).thenReturn(Optional.of(scenario));
        when(scenarioRepository.save(scenario)).thenReturn(scenario);

        assertFalse(service.deactivateScenario(scenario.getId()).isActive());
        assertTrue(service.activateScenario(scenario.getId()).isActive());
    }
}
