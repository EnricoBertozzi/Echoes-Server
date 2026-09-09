package com.n0hana.echoes_server.animal.dto;

import com.n0hana.echoes_server.animal.model.AnimalModel;

/**
 * AnimalDTO
 */
public record AnimalDTO(
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
