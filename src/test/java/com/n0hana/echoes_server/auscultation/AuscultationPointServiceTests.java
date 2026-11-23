package com.n0hana.echoes_server.auscultation;

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

import com.n0hana.echoes_server.animal.AnimalModel;
import com.n0hana.echoes_server.animal.AnimalService;
import com.n0hana.echoes_server.auscultation.exception.AuscultationPointInUseException;
import com.n0hana.echoes_server.device.DeviceRepository;
import com.n0hana.echoes_server.device.DeviceScenarioRepository;

@ExtendWith(MockitoExtension.class)
class AuscultationPointServiceTests {

    @Mock
    private AuscultationPointRepository pointRepository;

    @Mock
    private AnimalService animalService;

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private DeviceScenarioRepository deviceScenarioRepository;

    @InjectMocks
    private AuscultationPointService service;

    private AnimalModel animal;
    private AuscultationPointModel point;

    @BeforeEach
    void setUp() {
        animal = AnimalModel.builder().id(UUID.randomUUID()).name("Cachorro").build();
        point = AuscultationPointModel.builder().id(UUID.randomUUID()).position("Aorta").animal(animal).build();
    }

    @Test
    @DisplayName("Ponto de animal usado por um dispositivo não pode ser removido")
    void shouldNotDeletePointOfAnimalLinkedToDevice() {
        when(pointRepository.findById(point.getId())).thenReturn(Optional.of(point));
        when(deviceRepository.existsByAnimalId(animal.getId())).thenReturn(true);

        assertThrows(AuscultationPointInUseException.class,
                () -> service.deleteAuscultationPoint(point.getId(), null));
        verify(pointRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Ponto com cenário configurado em dispositivo não pode ser removido")
    void shouldNotDeletePointWithConfiguredScenario() {
        when(pointRepository.findById(point.getId())).thenReturn(Optional.of(point));
        when(deviceRepository.existsByAnimalId(animal.getId())).thenReturn(false);
        when(deviceScenarioRepository.existsByPointId(point.getId())).thenReturn(true);

        assertThrows(AuscultationPointInUseException.class,
                () -> service.deleteAuscultationPoint(point.getId(), null));
        verify(pointRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Ponto sem dispositivos associados é removido")
    void shouldDeletePointWithoutDevices() {
        when(pointRepository.findById(point.getId())).thenReturn(Optional.of(point));

        service.deleteAuscultationPoint(point.getId(), null);

        verify(pointRepository).delete(point);
    }

    @Test
    @DisplayName("Desativa e reativa um ponto de ausculta")
    void shouldDeactivateAndActivatePoint() {
        when(pointRepository.findById(point.getId())).thenReturn(Optional.of(point));
        when(pointRepository.save(point)).thenReturn(point);

        assertFalse(service.deactivateAuscultationPoint(point.getId()).isActive());
        assertTrue(service.activateAuscultationPoint(point.getId()).isActive());
    }
}
