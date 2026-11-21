package com.n0hana.echoes_server.device;

import org.javers.core.metamodel.annotation.ShallowReference;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.n0hana.echoes_server.animal.AnimalModel;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/** Um caso clinico do animal: para cada ponto, um cenario de audio existente. */
@Getter
@Setter
@Entity
@Table(name = "clinical_scenario_preset_tb",
    uniqueConstraints = @UniqueConstraint(columnNames = { "animal_id", "name" }))
public class ClinicalScenarioPresetModel {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ShallowReference
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @OnDelete(action = OnDeleteAction.CASCADE)
  @JoinColumn(name = "animal_id", nullable = false)
  private AnimalModel animal;

  @Column(name = "name", nullable = false, length = 100)
  private String name;

  @Column(name = "description", length = 255)
  private String description;

  @ElementCollection
  @CollectionTable(name = "clinical_scenario_preset_point_tb",
      joinColumns = @JoinColumn(name = "preset_id", nullable = false))
  @MapKeyColumn(name = "point_id", nullable = false)
  @Column(name = "scenario_id", nullable = false)
  private Map<UUID, UUID> selections = new LinkedHashMap<>();
}
