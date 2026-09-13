package com.n0hana.echoes_server.auscultation;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuscultationPointRepository extends JpaRepository<AuscultationPointModel, UUID>{
  
  Page<AuscultationPointModel> findAuscultationPointsByAnimalId(UUID animalId, Pageable pageable);
}
