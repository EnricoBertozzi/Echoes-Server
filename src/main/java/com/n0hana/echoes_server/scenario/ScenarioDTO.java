package com.n0hana.echoes_server.scenario;

import java.util.UUID;

import com.n0hana.echoes_server.auscultation.AuscultationPointModel;

/**
 * ScenarioDTO
 */
public class ScenarioDTO {
  public record ScenarioRegister(
      String name,
      String description,
      UUID pointId) {

    public ScenarioModel toModel() {
      return ScenarioModel.builder()
          .name(name)
          .description(description)
          .auscultationPoint(AuscultationPointModel.builder().id(pointId).build())
          .build();
    }
  }

  public record ScenarioUpdate(
      String name,
      String description) {

    public ScenarioModel toModel() {
      return ScenarioModel.builder()
          .name(name)
          .description(description)
          .build();
    }
  }

  public record ScenarioInfo(
      UUID id,
      String name,
      String description,
      String audioPath) {
    public static ScenarioInfo from(ScenarioModel model) {
      return new ScenarioInfo(
          model.getId(),
          model.getName(),
          model.getDescription(),
          model.getAudioUrl());
    }
  }
}
