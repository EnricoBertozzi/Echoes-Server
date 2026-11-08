package com.n0hana.echoes_server.animal;

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

@Service
public class AnimalService {

  @Autowired
  private AnimalRepository animalRepository;

  public List<AnimalModel> findAllAnimals(int page, int size) {
    return animalRepository
        .findAll(PageRequest.of(page, size, Sort.by("name")))
        .toList();
  }

  public List<AnimalModel> findAnimalsByName(String name, int page, int size) {
    return animalRepository.findByNameContaining(
        name,
        PageRequest.of(
            page,
            size,
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

    animalRepository.deleteById(id);
  }

  // Métodos auxiliares
  public void animalExists(UUID id) {
    animalRepository
        .findById(id)
        .orElseThrow(() -> new AnimalNotFoundException());
  }
}
