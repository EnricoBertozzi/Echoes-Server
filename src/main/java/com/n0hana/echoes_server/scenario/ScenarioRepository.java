package com.n0hana.echoes_server.scenario;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ScenarioRepository extends JpaRepository<ScenarioModel, UUID> {
  
}
