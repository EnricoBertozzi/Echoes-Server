package com.n0hana.echoes_server.auscultation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import com.n0hana.echoes_server.animal.AnimalModel;
import com.n0hana.echoes_server.animal.AnimalService;
import com.n0hana.echoes_server.auscultation.exception.AuscultationPointInUseException;
import com.n0hana.echoes_server.auscultation.exception.AuscultationPointNotFound;
import com.n0hana.echoes_server.device.DeviceModel;
import com.n0hana.echoes_server.device.DeviceRepository;
import com.n0hana.echoes_server.device.DeviceScenarioRepository;

@ExtendWith(MockitoExtension.class)
class AuscultationPointServiceCoverageTests {

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

    private AnimalModel animal(UUID id) {
        return AnimalModel.builder().id(id).build();
    }

    private AuscultationPointModel point(UUID animalId, String position) {
        return AuscultationPointModel.builder().id(UUID.randomUUID()).position(position)
                .animal(animalId == null ? null : animal(animalId)).build();
    }

    private DeviceModel device(UUID animalId) {
        DeviceModel d = new DeviceModel();
        if (animalId != null)
            d.setAnimal(animal(animalId));
        return d;
    }

    // ---------- listagens ----------

    @Test
    @DisplayName("Lista pontos do animal com clamp, ordenados por posição")
    void shouldFindAllByAnimal() {
        UUID animalId = UUID.randomUUID();
        AuscultationPointModel p = point(animalId, "Mitral");
        when(pointRepository.findAuscultationPointsByAnimalId(eq(animalId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(p)));

        List<AuscultationPointModel> result = service.findAllPointsByAnimal(-1, 0, animalId);

        assertEquals(List.of(p), result);
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(pointRepository).findAuscultationPointsByAnimalId(eq(animalId), captor.capture());
        assertEquals(0, captor.getValue().getPageNumber());
        assertEquals(1, captor.getValue().getPageSize());
        assertTrue(captor.getValue().getSort().getOrderFor("position") != null);
    }

    @Test
    @DisplayName("Dispositivo nulo ou sem animal não enxerga pontos")
    void shouldReturnEmptyWithoutAnimal() {
        assertTrue(service.findAllPointsByDevice(0, 10, null).isEmpty());
        assertTrue(service.findAllPointsByDevice(0, 10, new DeviceModel()).isEmpty());
        verifyNoInteractions(pointRepository);
    }

    @Test
    @DisplayName("Dispositivo com animal lista os pontos do seu animal")
    void shouldFindAllByDevice() {
        UUID animalId = UUID.randomUUID();
        AuscultationPointModel p = point(animalId, "Aórtica");
        when(pointRepository.findAuscultationPointsByAnimalId(eq(animalId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(p)));

        assertEquals(List.of(p), service.findAllPointsByDevice(0, 10, device(animalId)));
    }

    // ---------- findPointById ----------

    @Test
    @DisplayName("Busca ponto por id ou lança NotFound")
    void shouldFindByIdOrThrow() {
        AuscultationPointModel p = point(UUID.randomUUID(), "x");
        when(pointRepository.findById(p.getId())).thenReturn(Optional.of(p));
        UUID missing = UUID.randomUUID();
        when(pointRepository.findById(missing)).thenReturn(Optional.empty());

        assertSame(p, service.findPointById(p.getId()));
        service.auscultationPointExists(p.getId());
        assertThrows(AuscultationPointNotFound.class, () -> service.findPointById(missing));
        assertThrows(AuscultationPointNotFound.class, () -> service.auscultationPointExists(missing));
    }

    @Test
    @DisplayName("Sem dispositivo qualquer ponto é acessível")
    void shouldAccessWithoutDevice() {
        AuscultationPointModel p = point(UUID.randomUUID(), "x");
        when(pointRepository.findById(p.getId())).thenReturn(Optional.of(p));

        assertSame(p, service.findPointById(p.getId(), null));
    }

    @Test
    @DisplayName("Dispositivo acessa ponto do seu animal")
    void shouldAccessOwnPoint() {
        UUID animalId = UUID.randomUUID();
        AuscultationPointModel p = point(animalId, "x");
        when(pointRepository.findById(p.getId())).thenReturn(Optional.of(p));

        assertSame(p, service.findPointById(p.getId(), device(animalId)));
    }

    @Test
    @DisplayName("Dispositivo é negado para ponto de outro animal, ponto sem animal ou dispositivo sem animal")
    void shouldDenyForeignPoint() {
        UUID animalId = UUID.randomUUID();
        AuscultationPointModel other = point(UUID.randomUUID(), "x");
        AuscultationPointModel orphan = point(null, "y");
        AuscultationPointModel own = point(animalId, "z");
        when(pointRepository.findById(other.getId())).thenReturn(Optional.of(other));
        when(pointRepository.findById(orphan.getId())).thenReturn(Optional.of(orphan));
        when(pointRepository.findById(own.getId())).thenReturn(Optional.of(own));

        assertThrows(AccessDeniedException.class, () -> service.findPointById(other.getId(), device(animalId)));
        assertThrows(AccessDeniedException.class, () -> service.findPointById(orphan.getId(), device(animalId)));
        assertThrows(AccessDeniedException.class, () -> service.findPointById(own.getId(), device(null)));
    }

    // ---------- create ----------

    @Test
    @DisplayName("Criação por admin usa o animal informado no ponto")
    void shouldCreateWithPointAnimal() {
        UUID animalId = UUID.randomUUID();
        AnimalModel persisted = animal(animalId);
        AuscultationPointModel p = point(animalId, "Mitral");
        when(animalService.findAnimalById(animalId)).thenReturn(persisted);

        service.newAuscultationPoint(p, null);

        assertSame(persisted, p.getAnimal());
        verify(pointRepository).save(p);
    }

    @Test
    @DisplayName("Criação por dispositivo usa o animal do dispositivo")
    void shouldCreateWithDeviceAnimal() {
        UUID deviceAnimal = UUID.randomUUID();
        AnimalModel persisted = animal(deviceAnimal);
        AuscultationPointModel p = point(UUID.randomUUID(), "Mitral");
        when(animalService.findAnimalById(deviceAnimal)).thenReturn(persisted);

        service.newAuscultationPoint(p, device(deviceAnimal));

        assertSame(persisted, p.getAnimal());
        verify(pointRepository).save(p);
    }

    @Test
    @DisplayName("Criação por dispositivo sem animal é negada")
    void shouldDenyCreateByDeviceWithoutAnimal() {
        AuscultationPointModel p = point(UUID.randomUUID(), "x");

        assertThrows(AccessDeniedException.class, () -> service.newAuscultationPoint(p, device(null)));
        verify(pointRepository, never()).save(any());
    }

    @Test
    @DisplayName("Criação sem animal em lugar nenhum lança IllegalArgument")
    void shouldRejectCreateWithoutAnimal() {
        AuscultationPointModel p = point(null, "x");

        assertThrows(IllegalArgumentException.class, () -> service.newAuscultationPoint(p, null));
        verify(pointRepository, never()).save(any());
    }

    @Test
    @DisplayName("Criação com animal com id nulo lança IllegalArgument")
    void shouldRejectCreateWithNullAnimalId() {
        AuscultationPointModel p = AuscultationPointModel.builder().position("x")
                .animal(AnimalModel.builder().build()).build();

        assertThrows(IllegalArgumentException.class, () -> service.newAuscultationPoint(p, null));
    }

    // ---------- update ----------

    @Test
    @DisplayName("Atualiza a posição quando ela muda")
    void shouldUpdatePosition() {
        AuscultationPointModel saved = point(UUID.randomUUID(), "Velha");
        when(pointRepository.findById(saved.getId())).thenReturn(Optional.of(saved));

        service.updateAuscultationPoint(saved.getId(), AuscultationPointModel.builder().position("Nova").build(),
                null);

        assertEquals("Nova", saved.getPosition());
        verify(pointRepository).save(saved);
    }

    @Test
    @DisplayName("Mantém a posição quando nula ou igual")
    void shouldKeepPosition() {
        AuscultationPointModel saved = point(UUID.randomUUID(), "Igual");
        when(pointRepository.findById(saved.getId())).thenReturn(Optional.of(saved));

        service.updateAuscultationPoint(saved.getId(), AuscultationPointModel.builder().build(), null);
        service.updateAuscultationPoint(saved.getId(), AuscultationPointModel.builder().position("Igual").build(),
                null);

        assertEquals("Igual", saved.getPosition());
    }

    // ---------- delete / isInUse ----------

    @Test
    @DisplayName("Exclui ponto que não está em uso")
    void shouldDelete() {
        UUID animalId = UUID.randomUUID();
        AuscultationPointModel p = point(animalId, "x");
        when(pointRepository.findById(p.getId())).thenReturn(Optional.of(p));
        when(deviceRepository.existsByAnimalId(animalId)).thenReturn(false);
        when(deviceScenarioRepository.existsByPointId(p.getId())).thenReturn(false);

        service.deleteAuscultationPoint(p.getId(), null);

        verify(pointRepository).delete(p);
    }

    @Test
    @DisplayName("Não exclui ponto em uso")
    void shouldNotDeleteInUse() {
        UUID animalId = UUID.randomUUID();
        AuscultationPointModel p = point(animalId, "x");
        when(pointRepository.findById(p.getId())).thenReturn(Optional.of(p));
        when(deviceRepository.existsByAnimalId(animalId)).thenReturn(true);

        assertThrows(AuscultationPointInUseException.class, () -> service.deleteAuscultationPoint(p.getId(), null));
        verify(pointRepository, never()).delete(any());
    }

    @Test
    @DisplayName("isInUse: animal em dispositivo, cenário configurado, ponto sem animal e nenhum uso")
    void shouldComputeInUse() {
        UUID animalId = UUID.randomUUID();
        AuscultationPointModel withAnimal = point(animalId, "a");
        AuscultationPointModel orphan = point(null, "b");

        when(deviceRepository.existsByAnimalId(animalId)).thenReturn(true);
        assertTrue(service.isInUse(withAnimal));
        verify(deviceScenarioRepository, never()).existsByPointId(withAnimal.getId());

        when(deviceRepository.existsByAnimalId(animalId)).thenReturn(false);
        when(deviceScenarioRepository.existsByPointId(withAnimal.getId())).thenReturn(true);
        assertTrue(service.isInUse(withAnimal));

        when(deviceScenarioRepository.existsByPointId(orphan.getId())).thenReturn(false);
        assertFalse(service.isInUse(orphan));

        when(deviceScenarioRepository.existsByPointId(orphan.getId())).thenReturn(true);
        assertTrue(service.isInUse(orphan));
    }

    // ---------- ativar / desativar ----------

    @Test
    @DisplayName("Desativa ponto ativo e salva; ignora se já inativo")
    void shouldDeactivate() {
        AuscultationPointModel active = point(UUID.randomUUID(), "a");
        when(pointRepository.findById(active.getId())).thenReturn(Optional.of(active));
        when(pointRepository.save(active)).thenReturn(active);
        assertFalse(service.deactivateAuscultationPoint(active.getId()).isActive());

        AuscultationPointModel inactive = point(UUID.randomUUID(), "b");
        inactive.setActive(false);
        when(pointRepository.findById(inactive.getId())).thenReturn(Optional.of(inactive));
        assertFalse(service.deactivateAuscultationPoint(inactive.getId()).isActive());
        verify(pointRepository, never()).save(inactive);
    }

    @Test
    @DisplayName("Ativa ponto inativo e salva; ignora se já ativo")
    void shouldActivate() {
        AuscultationPointModel inactive = point(UUID.randomUUID(), "a");
        inactive.setActive(false);
        when(pointRepository.findById(inactive.getId())).thenReturn(Optional.of(inactive));
        when(pointRepository.save(inactive)).thenReturn(inactive);
        assertTrue(service.activateAuscultationPoint(inactive.getId()).isActive());

        AuscultationPointModel active = point(UUID.randomUUID(), "b");
        when(pointRepository.findById(active.getId())).thenReturn(Optional.of(active));
        assertTrue(service.activateAuscultationPoint(active.getId()).isActive());
        verify(pointRepository, never()).save(active);
    }
}
