package com.n0hana.echoes_server.device;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ClinicalScenarioPresetRepository extends JpaRepository<ClinicalScenarioPresetModel, UUID> {

  List<ClinicalScenarioPresetModel> findByAnimalIdOrderByNameAsc(UUID animalId);

  Optional<ClinicalScenarioPresetModel> findByIdAndAnimalId(UUID id, UUID animalId);

  boolean existsByAnimalIdAndNameIgnoreCase(UUID animalId, String name);
}
