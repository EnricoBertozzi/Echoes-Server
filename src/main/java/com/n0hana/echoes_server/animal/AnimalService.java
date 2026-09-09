package com.n0hana.echoes_server.animal;

import org.springframework.beans.factory.annotation.Autowired;
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
  
}
