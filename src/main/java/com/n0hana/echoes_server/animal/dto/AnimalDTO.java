package com.n0hana.echoes_server.animal.dto;

import java.util.UUID;

import com.n0hana.echoes_server.animal.model.AnimalModel;

/**
 * AnimalDTO
 */
public class AnimalDTO {
  public record Register(
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

  public record Update(
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

  public record Info(
      UUID id,
      String name,
      String description,
      String model) {
    public static Info from(AnimalModel animal) {
      return new Info(
          animal.getId(),
          animal.getName(),
          animal.getDescription(),
          animal.getModel());
    }
  }
}
