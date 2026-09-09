package com.n0hana.echoes_server.animal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.n0hana.echoes_server.animal.dto.AnimalDTO;

/**
 * AnimalController
 */
@RestController
@RequestMapping("/animal")
public class AnimalController {

  @Autowired
  private AnimalService animalService;

  @PostMapping
  public ResponseEntity<Void> saveAnimal(@RequestBody AnimalDTO dto) {
    animalService.save(dto.toModel());
    return ResponseEntity.ok().build();
  }
}
