package com.n0hana.echoes_server.auscultation;

import java.util.UUID;

import com.n0hana.echoes_server.animal.AnimalModel;

/**
 * AuscultationPointDTO
 */
public class AuscultationPointDTO {

  public record PointRegister(
      String position,
      UUID animalId) {

    public AuscultationPointModel toModel() {
      return AuscultationPointModel.builder()
          .position(position)
          .animal(AnimalModel.builder().id(animalId).build())
          .build();
    }
  }

  public record PointUpdate(String position) {

    public AuscultationPointModel toModel() {
      return AuscultationPointModel.builder()
          .position(position)
          .build();
    }
  }

  public record PointInfo(
      UUID id,
      String position,
      UUID animalId) {
    public static PointInfo from(AuscultationPointModel point) {
      return new PointInfo(
          point.getId(),
          point.getPosition(),
          point.getAnimal().getId());
    }
  }
}
