package com.n0hana.echoes_server.animal.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "auscultation_point_tb")
public class AuscultationPointModel {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  private String position;

  @ManyToOne(cascade = CascadeType.REMOVE)
  @JoinColumn(name = "animal")
  private AnimalModel animal;

  @OneToMany(mappedBy = "auscultationPoint", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
  private List<ScenarioModel> scenarios;

  public static AuscultationPointModelBuilder builder() {
    return new AuscultationPointModelBuilder();
  }

  public static class AuscultationPointModelBuilder {
    private UUID id;
    private String position;
    private AnimalModel animal;
    private List<ScenarioModel> scenarios;

    public AuscultationPointModelBuilder id(UUID id) {
      this.id = id;
      return this;
    }

    public AuscultationPointModelBuilder position(String position) {
      this.position = position;
      return this;
    }

    public AuscultationPointModelBuilder animal(AnimalModel animal) {
      this.animal = animal;
      return this;
    }

    public AuscultationPointModelBuilder scenarios(List<ScenarioModel> scenarios) {
      this.scenarios = scenarios;
      return this;
    }

    public AuscultationPointModel build() {
      AuscultationPointModel point = new AuscultationPointModel();
      point.setId(this.id);
      point.setPosition(this.position);
      point.setAnimal(animal);
      point.setScenarios(new ArrayList<>(this.scenarios));

      point.getScenarios().forEach(scenario -> scenario.setAuscultationPoint(point));
      return point;
    }
  }

}
