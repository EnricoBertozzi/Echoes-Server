package com.n0hana.echoes_server.animal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.n0hana.echoes_server.animal.model.AnimalModel;

@ExtendWith(MockitoExtension.class)
public class AnimalServiceTests {

  @Mock
  private AnimalRepository animalRepository;

  @InjectMocks
  private AnimalService animalService;

  @Test
  @DisplayName("Cadastro de novo animal com pontos de ausculta e cenários")
  public void saveNewAnimalInDatabase() {
    // Arrange

    AnimalModel animal = AnimalModel.builder()
        .name("Labrador")
        .description("Cão labrador para ausculta pulmonar e cardiaca")
        .model("Tches")
        .build();

    when(animalRepository.save(animal)).thenReturn(animal);

    // Act
    AnimalModel savedAnimal = animalService.save(animal);

    // Assert
    assertEquals(animal, savedAnimal);
  }

  @Test
  @DisplayName("Busca todos os animais cadastrados")
  public void findAnimalByPages() {
    // Arrange
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

    // Act
    List<AnimalModel> list = animalService.findAnimalsByName(name, page, size);

    // Assert
    assertNotNull(list);

    // Verifica se a pagina retornada possui os dois elementos
    assertEquals(2, list.size());

    // Verifica se todos os elementos foram retornados
    assertEquals(expectedAnimals, list);
  }

}
