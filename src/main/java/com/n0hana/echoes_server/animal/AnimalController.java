package com.n0hana.echoes_server.animal;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.n0hana.echoes_server.animal.dto.AnimalDTO;
import com.n0hana.echoes_server.animal.model.AnimalModel;

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

  @GetMapping
  public ResponseEntity<List<AnimalModel>> findAnimalsByName(@RequestParam String name, @RequestParam int page,
      @RequestParam int size) {
    return ResponseEntity.ok(
        animalService.findAnimalsByName(name, page, size));
  }
}
