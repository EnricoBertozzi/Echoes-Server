package com.n0hana.echoes_server.scenario;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScenarioRepository extends JpaRepository<ScenarioModel, UUID> {
  
  Page<ScenarioModel> findAllScenariosByAuscultationPointId(UUID auscultationId, Pageable pageable);

  Page<ScenarioModel> findAllScenariosByNameContaining(String name, Pageable pageable);
}
