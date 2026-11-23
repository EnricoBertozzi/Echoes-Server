package com.n0hana.echoes_server.animal;

import com.n0hana.echoes_server.animal.exception.AnimalInUseException;
import com.n0hana.echoes_server.device.DeviceRepository;
import com.n0hana.echoes_server.device.DeviceScenarioRepository;
import com.n0hana.echoes_server.infra.web.PageLimits;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.n0hana.echoes_server.animal.exception.AnimalNotFoundException;
import com.n0hana.echoes_server.infra.logs.Auditable;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AnimalService {

  private final AnimalRepository animalRepository;

  private final DeviceRepository deviceRepository;

  private final DeviceScenarioRepository deviceScenarioRepository;

  public List<AnimalModel> findAllAnimals(int page, int size) {
    return animalRepository
        .findAll(PageRequest.of(PageLimits.clampPage(page), PageLimits.clamp(size), Sort.by("name")))
        .toList();
  }

  public List<AnimalModel> findAnimalsByName(String name, int page, int size) {
    return animalRepository.findByNameContaining(
        name,
        PageRequest.of(PageLimits.clampPage(page), PageLimits.clamp(size),
            Sort.by("name")))
        .toList();
  }

  public AnimalModel findAnimalById(UUID id) throws AnimalNotFoundException {
    return animalRepository
        .findById(id)
        .orElseThrow(() -> new AnimalNotFoundException());
  }

  /**
   * Cadastra um modelo de animal no sistema.
   *
   * @param animal dados do animal
   * @return animal salvo
   */
  @Transactional
  @Auditable(action = "CREATE", entity = "Animal")
  public AnimalModel save(AnimalModel animal) {
    return animalRepository.save(animal);
  }

  @Transactional
  @Auditable(action = "UPDATE", entity = "Animal")
  public void updateAnimal(UUID id, AnimalModel animal) {
    AnimalModel savedAnimal = this.findAnimalById(id);

    if (animal.getName() != null && !Objects.equals(savedAnimal.getName(), animal.getName()))
      savedAnimal.setName(animal.getName());

    if (animal.getDescription() != null && !Objects.equals(savedAnimal.getDescription(), animal.getDescription()))
      savedAnimal.setDescription(animal.getDescription());

    if (animal.getModel() != null && !Objects.equals(savedAnimal.getModel(), animal.getModel()))
      savedAnimal.setModel(animal.getModel());

    animalRepository.save(savedAnimal);
  }

  @Transactional
  @Auditable(action = "DELETE", entity = "Animal")
  public void deleteAnimal(UUID id) {
    this.animalExists(id);

    if (isInUse(id))
      throw new AnimalInUseException();

    animalRepository.deleteById(id);
  }

  @Transactional
  @Auditable(action = "DEACTIVATE", entity = "Animal", entityId = "#id")
  public AnimalModel deactivateAnimal(UUID id) {
    return changeActive(id, false);
  }

  @Transactional
  @Auditable(action = "ACTIVATE", entity = "Animal", entityId = "#id")
  public AnimalModel activateAnimal(UUID id) {
    return changeActive(id, true);
  }

  /**
   * Indica se o animal está associado a algum dispositivo, diretamente ou por
   * um cenário configurado em um dos seus pontos.
   */
  public boolean isInUse(UUID id) {
    return deviceRepository.existsByAnimalId(id)
        || deviceScenarioRepository.existsByPointAnimalId(id);
  }

  private AnimalModel changeActive(UUID id, boolean active) {
    AnimalModel animal = this.findAnimalById(id);

    if (animal.isActive() == active)
      return animal;

    animal.setActive(active);
    return animalRepository.save(animal);
  }

  // Métodos auxiliares
  public void animalExists(UUID id) {
    animalRepository
        .findById(id)
        .orElseThrow(() -> new AnimalNotFoundException());
  }
}
