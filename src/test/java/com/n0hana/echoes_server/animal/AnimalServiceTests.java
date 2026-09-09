package com.n0hana.echoes_server.animal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.n0hana.echoes_server.animal.model.AnimalModel;
import com.n0hana.echoes_server.animal.model.AuscultationPointModel;
import com.n0hana.echoes_server.animal.model.ScenarioModel;

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

    ScenarioModel scenario1 = ScenarioModel.builder()
        .name("Sopro Cardiaco")
        .description("Sopro no coração")
        .audioUrl("http://localhost:8080")
        .build();

    ScenarioModel scenario2 = ScenarioModel.builder()
        .name("Palpitação")
        .description("Palpitação cardiaca")
        .audioUrl("http://localhost:8080")
        .build();

    ScenarioModel scenario3 = ScenarioModel.builder()
        .name("Tuberculose")
        .description("Tuberculose")
        .audioUrl("http://localhost:8080")
        .build();

    ScenarioModel scenario4 = ScenarioModel.builder()
        .name("Crepitação")
        .description("Crepitação")
        .audioUrl("http://localhost:8080")
        .build();

    AuscultationPointModel point1 = AuscultationPointModel.builder()
        .position("Coração")
        .scenarios(List.of(
            scenario1, scenario2))
        .build();

    AuscultationPointModel point2 = AuscultationPointModel.builder()
        .position("Pulmonar")
        .scenarios(List.of(
            scenario3, scenario4))
        .build();

    AnimalModel animal = AnimalModel.builder()
        .name("Labrador")
        .description("Cão labrador para ausculta pulmonar e cardiaca")
        .imageUrl("")
        .auscultationPoints(List.of(
            point1, point2))
        .build();

    when(animalRepository.save(animal)).thenReturn(animal);

    // Act
    AnimalModel savedAnimal = animalService.save(animal);

    // Assert
    assertEquals(animal, savedAnimal);
  }

  @Test
  @DisplayName("Cria um animal e adiciona pontos e cenários")
  public void createNewAnimalWithAuscultationPointsAndScenarios() {
    // Arrange
    ScenarioModel scenario1 = ScenarioModel.builder()
        .name("Sopro Cardiaco")
        .description("Sopro no coração")
        .audioUrl("http://localhost:8080")
        .build();

    ScenarioModel scenario2 = ScenarioModel.builder()
        .name("Palpitação")
        .description("Palpitação cardiaca")
        .audioUrl("http://localhost:8080")
        .build();

    ScenarioModel scenario3 = ScenarioModel.builder()
        .name("Tuberculose")
        .description("Tuberculose")
        .audioUrl("http://localhost:8080")
        .build();

    ScenarioModel scenario4 = ScenarioModel.builder()
        .name("Crepitação")
        .description("Crepitação")
        .audioUrl("http://localhost:8080")
        .build();

    AuscultationPointModel point1 = AuscultationPointModel.builder()
        .position("Coração")
        .scenarios(List.of(
            scenario1, scenario2))
        .build();

    AuscultationPointModel point2 = AuscultationPointModel.builder()
        .position("Pulmonar")
        .scenarios(List.of(
            scenario3, scenario4))
        .build();

    AnimalModel animal = AnimalModel.builder()
        .name("Labrador")
        .description("Cão labrador para ausculta pulmonar e cardiaca")
        .imageUrl("")
        .auscultationPoints(List.of(
            point1, point2))
        .build();

    // Assert

    assertEquals(2, animal.getAuscultationPoints().size());
    assertEquals(
        2,
        animal.getAuscultationPoints()
            .get(0)
            .getScenarios()
            .size());

    assertEquals(
        2,
        animal.getAuscultationPoints()
            .get(1)
            .getScenarios()
            .size());
  }

}
