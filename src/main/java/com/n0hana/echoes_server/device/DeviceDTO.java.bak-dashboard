package com.n0hana.echoes_server.device;

import java.util.UUID;

import com.n0hana.echoes_server.animal.AnimalModel;
import com.n0hana.echoes_server.institution.InstitutionModel;

import jakarta.validation.constraints.NotNull;

/**
 * DTOs do dispositivo. O cadastro não tem DTO: o próprio ESP se cadastra por
 * MQTT. O admin só aprova, move, ativa, inativa e exclui.
 */
public class DeviceDTO {

  public record DeviceInfo(
      UUID id,
      String name,
      String hardwareId,
      DeviceStatus status,
      UUID institutionId,
      String institutionName,
      UUID animalId,
      String animalName) {

    public static DeviceInfo from(DeviceModel device) {
      AnimalModel animal = device.getAnimal();
      InstitutionModel institution = device.getInstitution();

      UUID animalId = animal != null ? animal.getId() : null;
      String animalName = animal != null ? animal.getName() : null;
      UUID institutionId = institution != null ? institution.getId() : null;
      String institutionName = institution != null ? institution.getName() : null;

      return new DeviceInfo(
          device.getId(),
          device.getName(),
          device.getHardwareId(),
          device.getStatus(),
          institutionId,
          institutionName,
          animalId,
          animalName);
    }
  }

  /** Corpo de aprovação e de troca de instituição. */
  public record DeviceInstitutionRequest(@NotNull UUID institutionId) {
  }
}
