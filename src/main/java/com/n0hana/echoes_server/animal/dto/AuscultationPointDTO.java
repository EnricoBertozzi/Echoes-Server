package com.n0hana.echoes_server.animal.dto;

import java.util.List;

import com.n0hana.echoes_server.animal.model.AuscultationPointModel;

/**
 * AuscultationPointDTO
 */
public record AuscultationPointDTO(
    String position,
    List<ScenarioDTO> scenarios) {

  public AuscultationPointModel toModel() {
    return AuscultationPointModel.builder()
        .position(position)
        .scenarios(
            scenarios.stream().map(ScenarioDTO::toModel).toList())
        .build();
  }
}
