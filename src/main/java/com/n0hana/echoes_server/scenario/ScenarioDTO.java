package com.n0hana.echoes_server.scenario;

import java.util.UUID;

import com.n0hana.echoes_server.auscultation.AuscultationPointModel;

/**
 * ScenarioDTO
 */
public class ScenarioDTO {
  public record Register(
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
}
