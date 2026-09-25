package com.n0hana.echoes_server.animal;

import java.util.List;
import java.util.UUID;
import com.n0hana.echoes_server.infra.logs.Auditable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.n0hana.echoes_server.animal.exception.AnimalNotFoundException;

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

  @Transactional
  @Auditable (action = "CREATE", entity ="Animal")    
  public AnimalModel save(AnimalModel animal) {
    return animalRepository.save(animal);
  }

  @Transactional
  @Auditable (action = "UPDATE", entity = "Animal")
  public void updateAnimal(UUID id, AnimalModel animal) {
    AnimalModel savedAnimal = this.findAnimalById(id);

    if (!savedAnimal.getName().equals(animal.getName()))
      savedAnimal.setName(animal.getName());

    if (!savedAnimal.getDescription().equals(animal.getDescription()))
      savedAnimal.setDescription(animal.getDescription());

    if (!savedAnimal.getModel().equals(animal.getModel()))
      savedAnimal.setModel(animal.getModel());

    animalRepository.save(savedAnimal);
  }

  @Transactional
  @Auditable(action = "DELETE" , entity= "Animal")
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
