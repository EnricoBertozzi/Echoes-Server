package com.n0hana.echoes_server.animal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.n0hana.echoes_server.animal.exception.AnimalNotFoundException;

@ExtendWith(MockitoExtension.class)
public class AnimalServiceTests {

    @Mock
    private AnimalRepository animalRepository;

    @InjectMocks
    private AnimalService animalService;

    @Test
    @DisplayName("Cadastra um modelo de animal no banco de dados")
    void shouldSaveAnimal() {
        AnimalModel animal = AnimalModel.builder()
                .name("Cachorro")
                .description("Modelo canino para ausculta")
                .model("Canino")
                .build();

        when(animalRepository.save(animal)).thenReturn(animal);

        AnimalModel savedAnimal = animalService.save(animal);

        assertEquals(animal, savedAnimal);
        verify(animalRepository).save(animal);
    }

    @Test
    @DisplayName("Busca animal pelo id")
    void shouldFindAnimalById() {
        UUID id = UUID.randomUUID();
        AnimalModel animal = AnimalModel.builder()
                .id(id)
                .name("Gato")
                .build();

        when(animalRepository.findById(id)).thenReturn(Optional.of(animal));

        AnimalModel foundAnimal = animalService.findAnimalById(id);

        assertEquals(animal, foundAnimal);
    }

    @Test
    @DisplayName("Lança exceção ao buscar animal inexistente")
    void shouldThrowWhenAnimalDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(animalRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(AnimalNotFoundException.class, () -> animalService.findAnimalById(id));
    }

    @Test
    @DisplayName("Verifica a quantidade de elementos retornadas em uma página")
    void verifyFindAnimalsByNameWithPageSize() {
        AnimalModel animal1 = AnimalModel.builder()
                .name("Cachorro Juvenil")
                .description("Modelo Canino Juvenil")
                .model("Snoop")
                .build();

        AnimalModel animal2 = AnimalModel.builder()
                .name("Cachorro Adulto")
                .description("Modelo Canino Adulto")
                .model("Tches")
                .build();

        List<AnimalModel> expectedAnimals = List.of(animal1, animal2);

        int size = 2;
        int page = 0;
        String name = "Cachorro";

        Page<AnimalModel> mockPage = new PageImpl<>(
                expectedAnimals,
                PageRequest.of(page, size),
                3);

        when(animalRepository.findByNameContaining(
                name,
                PageRequest.of(
                        page,
                        size,
                        Sort.by("name"))))
                .thenReturn(mockPage);

        List<AnimalModel> list = animalService.findAnimalsByName(name, page, size);

        assertNotNull(list);
        assertEquals(2, list.size());
        assertEquals(expectedAnimals, list);
    }

    @Test
    @DisplayName("Verifica retorno vazio de busca além da quantidade de elementos")
    void verifyFindAnimalsByNameWithPageOutOfBounds() {
        int page = 5;
        int size = 2;
        String name = "Cachorro";

        Page<AnimalModel> mockPage = new PageImpl<>(
                List.of(),
                PageRequest.of(page, size),
                3);

        when(animalRepository.findByNameContaining(
                name,
                PageRequest.of(
                        page,
                        size,
                        Sort.by("name"))))
                .thenReturn(mockPage);

        List<AnimalModel> list = animalService.findAnimalsByName(name, page, size);

        assertNotNull(list);
        assertTrue(list.isEmpty());
    }

    @Test
    @DisplayName("Altera apenas os campos informados de um animal")
    void shouldUpdateOnlyProvidedFields() {
        UUID id = UUID.randomUUID();
        AnimalModel savedAnimal = AnimalModel.builder()
                .id(id)
                .name("Cachorro Juvenil")
                .description("Modelo de Canino Juvenil")
                .model("Tches")
                .build();

        AnimalModel update = AnimalModel.builder()
                .name("Cachorro Adulto")
                .build();

        when(animalRepository.findById(id)).thenReturn(Optional.of(savedAnimal));

        animalService.updateAnimal(id, update);

        assertEquals("Cachorro Adulto", savedAnimal.getName());
        assertEquals("Modelo de Canino Juvenil", savedAnimal.getDescription());
        assertEquals("Tches", savedAnimal.getModel());
        verify(animalRepository).save(savedAnimal);
    }

    @Test
    @DisplayName("Remove um animal existente")
    void shouldDeleteAnimal() {
        UUID id = UUID.randomUUID();
        when(animalRepository.findById(id)).thenReturn(Optional.of(AnimalModel.builder().id(id).build()));

        animalService.deleteAnimal(id);

        verify(animalRepository).deleteById(id);
    }

    @Test
    @DisplayName("Não remove animal inexistente")
    void shouldNotDeleteMissingAnimal() {
        UUID id = UUID.randomUUID();
        when(animalRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(AnimalNotFoundException.class, () -> animalService.deleteAnimal(id));

        verify(animalRepository, never()).deleteById(id);
    }
}
