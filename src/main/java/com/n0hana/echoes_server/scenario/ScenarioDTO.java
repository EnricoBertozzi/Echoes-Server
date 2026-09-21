package com.n0hana.echoes_server.scenario;

import java.util.UUID;

import org.hibernate.validator.constraints.Length;

import com.n0hana.echoes_server.auscultation.AuscultationPointModel;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * Conjunto de DTOs para consumo dos endpoints da API relacionados a entidade
 * {@link ScenarioModel}
 * 
 * @author Enrico Bertozzi
 * @since 0.1.0
 * @see {@link ScenarioController}
 */
public class ScenarioDTO {

  /**
   * DTO para cadastro de novos cenários no sistema.
   * 
   * @param name        Nome do novo cenário
   * @param description Descrição do cenário
   * @param pointId     Identificador do ponto de ausculta
   */
  public record ScenarioRegister(
      @NotEmpty(message = "Nome é obrigatório") @Length(min = 3, max = 25) String name,
      @NotEmpty(message = "Descrição é obrigatório") @Length(min = 6, max = 100) String description,
      @NotNull(message = "Identificador não pode ser nulo") UUID pointId) {

    /**
     * Mapeia um objeto DTO {@link ScenarioDTO.ScenarioRegister} para um objeto do
     * domínio da aplicação {@link ScenarioModel}
     * 
     * @return {@link ScenarioModel} com dados mapeados do objeto DTO
     */
    public ScenarioModel toModel() {
      return ScenarioModel.builder()
          .name(name)
          .description(description)
          .auscultationPoint(AuscultationPointModel.builder().id(pointId).build())
          .build();
    }
  }

  /**
   * DTO para atualização de cenários no sistema.
   * 
   * @param name        Nome do novo cenário
   * @param description Descrição do cenário
   */
  public record ScenarioUpdate(
      @NotEmpty(message = "Nome é obrigatório") @Length(min = 3, max = 25) String name,
      @NotEmpty(message = "Descrição é obrigatório") @Length(min = 6, max = 100) String description) {

    /**
     * Mapeia um objeto DTO {@link ScenarioDTO.ScenarioUpdate} para um objeto do
     * domínio da aplicação {@link ScenarioModel}
     * 
     * @return {@link ScenarioModel} com dados mapeados do objeto DTO
     */
    public ScenarioModel toModel() {
      return ScenarioModel.builder()
          .name(name)
          .description(description)
          .build();
    }
  }

  /**
   * DTO para exibição dos dados de um cenário no sistema.
   * 
   * @param id          Identificador do cenário
   * @param name        Nome do novo cenário
   * @param description Descrição do cenário
   * @param audioPath   Caminho/Nome do arquivo de áudio salvo no sistema
   */
  public record ScenarioInfo(
      UUID id,
      String name,
      String description,
      String audioPath) {

    /**
     * Mapeia um objeto do domínio da aplicalçao {@link ScenarioModel} para um
     * objeto DTO {@link ScenarioDTO.ScenarioInfo}.
     * 
     * @return {@link ScenarioDTO.ScenarioInfo} com dados mapeados do objeto model.
     */
    public static ScenarioInfo from(ScenarioModel model) {
      return new ScenarioInfo(
          model.getId(),
          model.getName(),
          model.getDescription(),
          model.getAudioUrl());
    }
  }
}
