package com.n0hana.echoes_server.auscultation;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuscultationPointRepository extends JpaRepository<AuscultationPointModel, UUID>{
  
  Page<AuscultationPointModel> findAuscultationPointsByAnimalId(UUID animalId, Pageable pageable);

  // todos os pontos do animal em ordem de posição, usado na sincronização do ESP
  List<AuscultationPointModel> findByAnimalIdOrderByPositionAsc(UUID animalId);
}
