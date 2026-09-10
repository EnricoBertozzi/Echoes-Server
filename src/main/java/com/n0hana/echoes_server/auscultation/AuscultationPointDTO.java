package com.n0hana.echoes_server.auscultation;

import java.util.UUID;

import com.n0hana.echoes_server.animal.AnimalModel;

/**
 * AuscultationPointDTO
 */
public class AuscultationPointDTO {

  public record Register(
      String position,
      UUID animalId) {

    public AuscultationPointModel toModel() {
      return AuscultationPointModel.builder()
          .position(position)
          .animal(AnimalModel.builder().id(animalId).build())
          .build();
    }
  }

  public record Update(String position) {

    public AuscultationPointModel toModel() {
      return AuscultationPointModel.builder()
          .position(position)
          .build();
    }
  }

  public record Info(
      UUID id,
      String position,
      UUID animalId) {
    public static Info from(AuscultationPointModel point) {
      return new Info(
          point.getId(),
          point.getPosition(),
          point.getAnimal().getId());
    }
  }
}
