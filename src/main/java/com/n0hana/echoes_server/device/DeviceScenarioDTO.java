package com.n0hana.echoes_server.device;

import java.util.List;
import java.util.UUID;

import com.n0hana.echoes_server.scenario.ScenarioModel;

import jakarta.validation.constraints.NotNull;

/**
 * DTOs da configuração de cenários de um dispositivo. É o contrato que o ESP lê
 * para sincronizar: os pontos do animal, cada um com o cenário atual e a URI do
 * áudio.
 */
public class DeviceScenarioDTO {

  /** Corpo para escolher o cenário ativo de um ponto. */
  public record SelectScenarioRequest(
      @NotNull(message = "O cenário é obrigatório") UUID scenarioId) {
  }

  /**
   * Cenário atual de um ponto.
   *
   * @param audioUri Caminho para baixar o áudio, relativo à URL base da API
   */
  public record ScenarioRef(UUID id, String name, String description, String audioUri) {

    public static ScenarioRef from(ScenarioModel scenario) {
      return new ScenarioRef(
          scenario.getId(),
          scenario.getName(),
          scenario.getDescription(),
          "/api/v1/scenarios/" + scenario.getId() + "/audio");
    }
  }

  /** Ponto de ausculta; {@code scenario} é nulo enquanto nenhum foi escolhido. */
  public record PointConfig(UUID pointId, String position, ScenarioRef scenario) {
  }

  public record DeviceConfig(UUID deviceId, List<PointConfig> points) {
  }
}
