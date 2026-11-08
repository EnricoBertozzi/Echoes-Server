package com.n0hana.echoes_server.animal;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.n0hana.echoes_server.animal.exception.AnimalMacAlreadyInUseException;
import com.n0hana.echoes_server.animal.exception.AnimalNotFoundException;
import com.n0hana.echoes_server.animal.exception.InvalidAnimalMacException;
import com.n0hana.echoes_server.infra.logs.Auditable;

import jakarta.transaction.Transactional;

@Service
public class AnimalService {

  @Autowired
  private AnimalRepository animalRepository;

  @Autowired
  private AnimalPublicKeyService publicKeyService;

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
   * Busca o animal pelo MAC informado no token do dispositivo.
   *
   * @param mac MAC do dispositivo
   * @return animal correspondente
   */
  public AnimalModel findAnimalByMac(String mac) throws AnimalNotFoundException {
    String normalizedMac = normalizeMac(mac);
    if (normalizedMac == null || normalizedMac.isBlank())
      throw new AnimalNotFoundException();

    return animalRepository
        .findByMac(normalizedMac)
        .orElseThrow(() -> new AnimalNotFoundException());
  }

  /**
   * Cadastra um animal recebendo a chave pública como arquivo. O arquivo não é
   * armazenado: apenas o seu conteúdo é lido e salvo em {@code publicKey}.
   *
   * @param animal        dados do animal
   * @param publicKeyFile arquivo contendo a chave pública PEM
   * @return animal salvo
   */
  @Transactional
  @Auditable(action = "CREATE", entity = "Animal")
  public AnimalModel save(AnimalModel animal, MultipartFile publicKeyFile) {
    String mac = normalizeMac(animal.getMac());
    validateMac(mac, null);
    animal.setMac(mac);

    animal.setPublicKey(publicKeyService.readFrom(publicKeyFile));
    return animalRepository.save(animal);
  }

  @Transactional
  @Auditable(action = "UPDATE", entity = "Animal")
  public void updateAnimal(UUID id, AnimalModel animal, MultipartFile publicKeyFile) {
    AnimalModel savedAnimal = this.findAnimalById(id);

    if (animal.getName() != null && !Objects.equals(savedAnimal.getName(), animal.getName()))
      savedAnimal.setName(animal.getName());

    if (animal.getDescription() != null && !Objects.equals(savedAnimal.getDescription(), animal.getDescription()))
      savedAnimal.setDescription(animal.getDescription());

    if (animal.getModel() != null && !Objects.equals(savedAnimal.getModel(), animal.getModel()))
      savedAnimal.setModel(animal.getModel());

    String mac = normalizeMac(animal.getMac());
    if (mac != null && !mac.isBlank() && !Objects.equals(savedAnimal.getMac(), mac)) {
      validateMac(mac, savedAnimal.getId());
      savedAnimal.setMac(mac);
    }

    if (publicKeyFile != null && !publicKeyFile.isEmpty())
      savedAnimal.setPublicKey(publicKeyService.readFrom(publicKeyFile));

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

  private void validateMac(String mac, UUID currentAnimalId) {
    if (mac == null || mac.isBlank())
      throw new InvalidAnimalMacException();

    animalRepository.findByMac(mac).ifPresent(existingAnimal -> {
      if (currentAnimalId == null || !existingAnimal.getId().equals(currentAnimalId))
        throw new AnimalMacAlreadyInUseException();
    });
  }

  private String normalizeMac(String mac) {
    return mac != null ? mac.trim() : null;
  }
}
