package com.n0hana.echoes_server.animal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.n0hana.echoes_server.animal.exception.AnimalNotFoundException;
import com.n0hana.echoes_server.animal.model.AnimalModel;

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
  public AnimalModel save(AnimalModel animal) {
    return animalRepository.save(animal);
  }

  @Transactional
  public void updateAnimal(UUID id, AnimalModel animal) {
    this.animalExists(id);

    animal.setId(id);

    animalRepository.save(animal);
  }

  @Transactional
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
