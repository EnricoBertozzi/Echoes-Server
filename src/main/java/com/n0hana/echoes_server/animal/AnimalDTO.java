package com.n0hana.echoes_server.animal;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;

/**
 * AnimalDTO
 */
public class AnimalDTO {

  /**
   * Dados de cadastro do animal. A chave pública é enviada separadamente como
   * arquivo multipart e persistida como texto no campo {@code publicKey}.
   */
  public record AnimalRegister(
      String name,
      String description,
      String model,
      @NotBlank(message = "MAC é obrigatório") String mac) {

    public AnimalModel toModel() {
      return AnimalModel.builder()
          .name(name)
          .description(description)
          .model(model)
          .mac(mac)
          .build();
    }
  }

  public record AnimalUpdate(
      String name,
      String description,
      String model,
      String mac) {

    public AnimalModel toModel() {
      return AnimalModel.builder()
          .name(name)
          .description(description)
          .model(model)
          .mac(mac)
          .build();
    }
  }

  public record AnimalInfo(
      UUID id,
      String name,
      String description,
      String model,
      String mac) {
    public static AnimalInfo from(AnimalModel animal) {
      return new AnimalInfo(
          animal.getId(),
          animal.getName(),
          animal.getDescription(),
          animal.getModel(),
          animal.getMac());
    }
  }
}
