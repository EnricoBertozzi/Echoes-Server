package com.n0hana.echoes_server.device;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.n0hana.echoes_server.animal.AnimalModel;
import com.n0hana.echoes_server.animal.AnimalRepository;

import jakarta.persistence.EntityManager;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DevicePersistenceIntegrationTest {

  @Autowired
  private DeviceRepository deviceRepository;

  @Autowired
  private AnimalRepository animalRepository;

  @Autowired
  private EntityManager entityManager;

  @Test
  @DisplayName("Persiste dispositivo sem animal associado")
  void shouldPersistDeviceWithoutAnimal() {
    DeviceModel device = DeviceModel.builder()
        .name("Dispositivo sem animal")
        .mac("AA:BB:CC:DD:EE:01")
        .publicKey("pem")
        .build();

    DeviceModel saved = deviceRepository.saveAndFlush(device);
    entityManager.clear();

    DeviceModel found = deviceRepository.findById(saved.getId()).orElseThrow();

    assertNull(found.getAnimal());
  }

  @Test
  @DisplayName("Persiste dispositivo com animal associado")
  void shouldPersistDeviceWithAnimal() {
    AnimalModel animal = animalRepository.saveAndFlush(
        AnimalModel.builder().name("Cachorro").build());

    DeviceModel device = DeviceModel.builder()
        .name("Dispositivo com animal")
        .mac("AA:BB:CC:DD:EE:02")
        .publicKey("pem")
        .animal(animal)
        .build();

    DeviceModel saved = deviceRepository.saveAndFlush(device);
    entityManager.clear();

    DeviceModel found = deviceRepository.findById(saved.getId()).orElseThrow();

    assertNotNull(found.getAnimal());
  }

  @Test
  @DisplayName("Remove a associação quando o animal é excluído")
  void shouldClearAnimalWhenAnimalIsDeleted() {
    AnimalModel animal = animalRepository.saveAndFlush(
        AnimalModel.builder().name("Gato").build());

    DeviceModel device = deviceRepository.saveAndFlush(DeviceModel.builder()
        .name("Dispositivo associado")
        .mac("AA:BB:CC:DD:EE:03")
        .publicKey("pem")
        .animal(animal)
        .build());

    UUID animalId = animal.getId();
    UUID deviceId = device.getId();

    entityManager.flush();
    entityManager.clear();

    animalRepository.deleteById(animalId);
    animalRepository.flush();
    entityManager.clear();

    DeviceModel found = deviceRepository.findById(deviceId).orElseThrow();

    assertNull(found.getAnimal());
  }
}
