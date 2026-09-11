package com.n0hana.echoes_server.animal;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.n0hana.echoes_server.animal.AnimalDTO.AnimalInfo;

/**
 * AnimalController
 */
@RestController
@RequestMapping("/animals")
public class AnimalController {

  @Autowired
  private AnimalService animalService;

  /**
   * Criar um novo animal
   */
  @PostMapping
  public ResponseEntity<Void> newAnimal(@RequestBody AnimalDTO.AnimalRegister dto) {
    animalService.save(dto.toModel());
    return ResponseEntity.ok().build();
  }

  /**
   * Busca todos os animais
   */
  @GetMapping
  public ResponseEntity<List<AnimalDTO.AnimalInfo>> findAllAnimals(@RequestParam int page, @RequestParam int size) {
    List<AnimalDTO.AnimalInfo> list = animalService
        .findAllAnimals(page, size)
        .stream()
        .map(AnimalDTO.AnimalInfo::from)
        .toList();

    return ResponseEntity.ok(list);
  }

  /**
   * Busca por animais com mesmo nome
   */
  @GetMapping("/search")
  public ResponseEntity<List<AnimalDTO.AnimalInfo>> findAnimalsByName(@RequestParam String name, @RequestParam int page,
      @RequestParam int size) {
    List<AnimalDTO.AnimalInfo> list = animalService
        .findAnimalsByName(name, page, size)
        .stream()
        .map(AnimalDTO.AnimalInfo::from)
        .toList();

    return ResponseEntity.ok(list);
  }

  /**
   * Busca um animal pelo id
   */
  @GetMapping("/{id}")
  public ResponseEntity<AnimalDTO.AnimalInfo> findAnimalById(@PathVariable("id") UUID id) {
    return ResponseEntity
        .ok(AnimalInfo.from(animalService.findAnimalById(id)));
  }

  /**
   * Atualiza os dados de um animal
   */
  @PatchMapping("/{id}")
  public ResponseEntity<Void> updateAnimal(@PathVariable("id") UUID id, @RequestBody AnimalDTO.AnimalUpdate dto) {
    animalService.updateAnimal(id, dto.toModel());
    return ResponseEntity.ok().build();
  }

  /**
   * Remove um animal do banco de dados
   */
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteAnimal(@PathVariable("id") UUID id) {
    animalService.deleteAnimal(id);
    return ResponseEntity.ok().build();
  }

}
