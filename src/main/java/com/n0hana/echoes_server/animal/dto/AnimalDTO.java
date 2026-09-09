package com.n0hana.echoes_server.animal.dto;

import java.util.List;

import com.n0hana.echoes_server.animal.model.AnimalModel;

/**
 * AnimalDTO
 */
public record AnimalDTO(
    String name,
    String description,
    String imageUrl,
    List<AuscultationPointDTO> auscultationPoints) {

  public AnimalModel toModel() {
    return AnimalModel.builder()
        .name(name)
        .description(description)
        .imageUrl(imageUrl)
        .auscultationPoints(
            auscultationPoints.stream().map(AuscultationPointDTO::toModel).toList())
        .build();
  }

}
