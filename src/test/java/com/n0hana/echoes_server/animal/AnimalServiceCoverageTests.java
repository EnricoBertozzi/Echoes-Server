package com.n0hana.echoes_server.animal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

import com.n0hana.echoes_server.animal.exception.AnimalInUseException;
import com.n0hana.echoes_server.animal.exception.AnimalNotFoundException;
import com.n0hana.echoes_server.device.DeviceRepository;
import com.n0hana.echoes_server.device.DeviceScenarioRepository;

@ExtendWith(MockitoExtension.class)
class AnimalServiceCoverageTests {

    @Mock
    private AnimalRepository animalRepository;
    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private DeviceScenarioRepository deviceScenarioRepository;

    @InjectMocks
    private AnimalService service;

    private AnimalModel animal(String name, String description, String model) {
        return AnimalModel.builder().id(UUID.randomUUID()).name(name).description(description).model(model).build();
    }

    @Test
    @DisplayName("Lista animais com clamp e ordenação por nome")
    void shouldFindAllClamped() {
        AnimalModel a = animal("Gato", "d", "m");
        when(animalRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(a)));

        assertEquals(List.of(a), service.findAllAnimals(-3, 9999));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(animalRepository).findAll(captor.capture());
        assertEquals(0, captor.getValue().getPageNumber());
        assertEquals(100, captor.getValue().getPageSize());
        assertTrue(captor.getValue().getSort().getOrderFor("name") != null);
    }

    @Test
    @DisplayName("Busca animais por nome")
    void shouldFindByName() {
        AnimalModel a = animal("Gato", "d", "m");
        when(animalRepository.findByNameContaining(org.mockito.ArgumentMatchers.eq("Ga"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(a)));

        assertEquals(List.of(a), service.findAnimalsByName("Ga", 0, 10));
    }

    @Test
    @DisplayName("Busca por id e existência lançam NotFound quando ausente")
    void shouldFindByIdOrThrow() {
        AnimalModel a = animal("Gato", "d", "m");
        UUID missing = UUID.randomUUID();
        when(animalRepository.findById(a.getId())).thenReturn(Optional.of(a));
        when(animalRepository.findById(missing)).thenReturn(Optional.empty());

        assertEquals(a, service.findAnimalById(a.getId()));
        service.animalExists(a.getId());
        assertThrows(AnimalNotFoundException.class, () -> service.findAnimalById(missing));
        assertThrows(AnimalNotFoundException.class, () -> service.animalExists(missing));
    }

    @Test
    @DisplayName("Atualiza todos os campos que mudaram")
    void shouldUpdateAllFields() {
        AnimalModel saved = animal("Velho", "Desc velha", "Modelo velho");
        when(animalRepository.findById(saved.getId())).thenReturn(Optional.of(saved));

        service.updateAnimal(saved.getId(), animal("Novo", "Desc nova", "Modelo novo"));

        assertEquals("Novo", saved.getName());
        assertEquals("Desc nova", saved.getDescription());
        assertEquals("Modelo novo", saved.getModel());
        verify(animalRepository).save(saved);
    }

    @Test
    @DisplayName("Campos nulos no update não sobrescrevem os existentes")
    void shouldIgnoreNullFields() {
        AnimalModel saved = animal("Nome", "Desc", "Modelo");
        when(animalRepository.findById(saved.getId())).thenReturn(Optional.of(saved));

        service.updateAnimal(saved.getId(), AnimalModel.builder().build());

        assertEquals("Nome", saved.getName());
        assertEquals("Desc", saved.getDescription());
        assertEquals("Modelo", saved.getModel());
        verify(animalRepository).save(saved);
    }

    @Test
    @DisplayName("Campos iguais no update são mantidos")
    void shouldKeepEqualFields() {
        AnimalModel saved = animal("Nome", "Desc", "Modelo");
        when(animalRepository.findById(saved.getId())).thenReturn(Optional.of(saved));

        service.updateAnimal(saved.getId(), animal("Nome", "Desc", "Modelo"));

        assertEquals("Nome", saved.getName());
        assertEquals("Desc", saved.getDescription());
        assertEquals("Modelo", saved.getModel());
    }

    @Test
    @DisplayName("Update de animal inexistente lança NotFound sem salvar")
    void shouldFailUpdateMissing() {
        UUID id = UUID.randomUUID();
        when(animalRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(AnimalNotFoundException.class, () -> service.updateAnimal(id, animal("a", "b", "c")));
        verify(animalRepository, never()).save(any());
    }

    @Test
    @DisplayName("Exclui animal livre; bloqueia quando usado por dispositivo ou por cenário")
    void shouldDeleteOrBlock() {
        AnimalModel free = animal("a", "b", "c");
        when(animalRepository.findById(free.getId())).thenReturn(Optional.of(free));
        when(deviceRepository.existsByAnimalId(free.getId())).thenReturn(false);
        when(deviceScenarioRepository.existsByPointAnimalId(free.getId())).thenReturn(false);
        service.deleteAnimal(free.getId());
        verify(animalRepository).deleteById(free.getId());

        AnimalModel byDevice = animal("d", "e", "f");
        when(animalRepository.findById(byDevice.getId())).thenReturn(Optional.of(byDevice));
        when(deviceRepository.existsByAnimalId(byDevice.getId())).thenReturn(true);
        assertThrows(AnimalInUseException.class, () -> service.deleteAnimal(byDevice.getId()));
        verify(animalRepository, never()).deleteById(byDevice.getId());

        AnimalModel byScenario = animal("g", "h", "i");
        when(animalRepository.findById(byScenario.getId())).thenReturn(Optional.of(byScenario));
        when(deviceRepository.existsByAnimalId(byScenario.getId())).thenReturn(false);
        when(deviceScenarioRepository.existsByPointAnimalId(byScenario.getId())).thenReturn(true);
        assertThrows(AnimalInUseException.class, () -> service.deleteAnimal(byScenario.getId()));
        assertTrue(service.isInUse(byScenario.getId()));
    }

    @Test
    @DisplayName("Delete de animal inexistente lança NotFound")
    void shouldFailDeleteMissing() {
        UUID id = UUID.randomUUID();
        when(animalRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(AnimalNotFoundException.class, () -> service.deleteAnimal(id));
        verify(animalRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("isInUse é falso sem dispositivo nem cenário")
    void shouldNotBeInUse() {
        UUID id = UUID.randomUUID();
        when(deviceRepository.existsByAnimalId(id)).thenReturn(false);
        when(deviceScenarioRepository.existsByPointAnimalId(id)).thenReturn(false);

        assertFalse(service.isInUse(id));
    }

    @Test
    @DisplayName("Desativa animal ativo; ignora se já inativo")
    void shouldDeactivate() {
        AnimalModel active = animal("a", "b", "c");
        when(animalRepository.findById(active.getId())).thenReturn(Optional.of(active));
        when(animalRepository.save(active)).thenReturn(active);
        assertFalse(service.deactivateAnimal(active.getId()).isActive());

        AnimalModel inactive = animal("d", "e", "f");
        inactive.setActive(false);
        when(animalRepository.findById(inactive.getId())).thenReturn(Optional.of(inactive));
        assertFalse(service.deactivateAnimal(inactive.getId()).isActive());
        verify(animalRepository, never()).save(inactive);
    }

    @Test
    @DisplayName("Ativa animal inativo; ignora se já ativo")
    void shouldActivate() {
        AnimalModel inactive = animal("a", "b", "c");
        inactive.setActive(false);
        when(animalRepository.findById(inactive.getId())).thenReturn(Optional.of(inactive));
        when(animalRepository.save(inactive)).thenReturn(inactive);
        assertTrue(service.activateAnimal(inactive.getId()).isActive());

        AnimalModel active = animal("d", "e", "f");
        when(animalRepository.findById(active.getId())).thenReturn(Optional.of(active));
        assertTrue(service.activateAnimal(active.getId()).isActive());
        verify(animalRepository, never()).save(active);
    }
}
