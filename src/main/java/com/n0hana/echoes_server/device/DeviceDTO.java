package com.n0hana.echoes_server.device;

import java.util.UUID;

import com.n0hana.echoes_server.animal.AnimalModel;

/**
 * DTOs de leitura do dispositivo. O cadastro é feito diretamente no banco de
 * dados, portanto não há DTO de criação ou atualização.
 */
public class DeviceDTO {

  public record DeviceInfo(
      UUID id,
      String name,
      String mac,
      UUID animalId,
      String animalName) {

    public static DeviceInfo from(DeviceModel device) {
      AnimalModel animal = device.getAnimal();

      UUID animalId = animal != null ? animal.getId() : null;
      String animalName = animal != null ? animal.getName() : null;

      return new DeviceInfo(
          device.getId(),
          device.getName(),
          device.getMac(),
          animalId,
          animalName);
    }
  }
}
