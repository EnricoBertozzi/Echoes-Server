package com.n0hana.echoes_server.animal;

import java.util.UUID;

/**
 * AnimalDTO
 */
public class AnimalDTO {
  public record AnimalRegister(
      String name,
      String description,
      String model) {

    public AnimalModel toModel() {
      return AnimalModel.builder()
          .name(name)
          .description(description)
          .model(model)
          .build();
    }
  }

  public record AnimalUpdate(
      String name,
      String description,
      String model) {

    public AnimalModel toModel() {
      return AnimalModel.builder()
          .name(name)
          .description(description)
          .model(model)
          .build();
    }
  }

  public record AnimalInfo(
      UUID id,
      String name,
      String description,
      String model) {
    public static AnimalInfo from(AnimalModel animal) {
      return new AnimalInfo(
          animal.getId(),
          animal.getName(),
          animal.getDescription(),
          animal.getModel());
    }
  }
}
