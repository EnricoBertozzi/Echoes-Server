package com.n0hana.echoes_server.animal;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.n0hana.echoes_server.animal.model.AnimalModel;

import jakarta.transaction.Transactional;

@Service
public class AnimalService {

  @Autowired
  private AnimalRepository animalRepository;

  @Transactional
  public AnimalModel save(AnimalModel animal) {
    return animalRepository.save(animal);
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

}
