package com.n0hana.echoes_server.animal;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.n0hana.echoes_server.animal.model.AnimalModel;

public interface AnimalRepository extends JpaRepository<AnimalModel, UUID> {
  
}
