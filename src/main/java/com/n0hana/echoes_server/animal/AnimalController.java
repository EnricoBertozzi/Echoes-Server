package com.n0hana.echoes_server.animal;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.n0hana.echoes_server.animal.AnimalDTO.AnimalInfo;
import com.n0hana.echoes_server.infra.security.SecurityConfig;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Encoding;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;

/**
 * Controller REST responsável pelo gerenciamento de animais
 * 
 * @author Enrico Bertozzi
 * @since 0.1.0
 * @see {@link AnimalModel}
 * @see {@link AnimalService}
 */
@RestController
@RequestMapping("/animals")
@SecurityRequirement(name = SecurityConfig.SECURITY)
public class AnimalController {

  @Autowired
  private AnimalService animalService;

  /**
   * Registra um novo animal no sistema.
   *
   * <p>
   * A chave pública deve ser enviada como arquivo no campo {@code publicKey}.
   * O arquivo não é armazenado: o servidor lê o seu conteúdo e persiste o
   * texto diretamente no animal.
   * </p>
   *
   * @param dto           Dados de cadastro do animal
   * @param publicKeyFile Arquivo com a chave pública PEM
   * @return {@link AnimalInfo} contendo os dados do novo animal.
   */
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @RequestBody(content = @Content(encoding = @Encoding(name = "dto", contentType = MediaType.APPLICATION_JSON_VALUE)))
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<AnimalInfo> save(
      @RequestPart("dto") @Valid AnimalDTO.AnimalRegister dto,
      @RequestPart("publicKey") MultipartFile publicKeyFile) {

    AnimalInfo animal = AnimalInfo.from(animalService.save(dto.toModel(), publicKeyFile));

    return ResponseEntity.ok(animal);
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
   * Atualiza os dados de um animal. O arquivo de chave pública é opcional: se
   * enviado, substitui a chave atual.
   */
  @PatchMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @RequestBody(content = @Content(encoding = @Encoding(name = "dto", contentType = MediaType.APPLICATION_JSON_VALUE)))
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Void> updateAnimal(
      @PathVariable("id") UUID id,
      @RequestPart("dto") AnimalDTO.AnimalUpdate dto,
      @RequestPart(value = "publicKey", required = false) MultipartFile publicKeyFile) {

    animalService.updateAnimal(id, dto.toModel(), publicKeyFile);
    return ResponseEntity.ok().build();
  }

  /**
   * Remove um animal do banco de dados
   */
  @PreAuthorize("hasRole('ADMIN')")
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteAnimal(@PathVariable("id") UUID id) {
    animalService.deleteAnimal(id);
    return ResponseEntity.ok().build();
  }

}
