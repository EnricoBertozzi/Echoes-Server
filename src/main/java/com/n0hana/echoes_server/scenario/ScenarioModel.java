package com.n0hana.echoes_server.scenario;

import java.util.UUID;

import com.n0hana.echoes_server.auscultation.AuscultationPointModel;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "scenario_tb")
public class ScenarioModel {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  private String name;

  private String description;

  private String audioUrl;

  @ManyToOne(cascade = CascadeType.REMOVE)
  @JoinColumn(name = "auscultation_point")
  private AuscultationPointModel auscultationPoint;

  public static ScenarioModelBuilder builder() {
    return new ScenarioModelBuilder();
  }

  public static class ScenarioModelBuilder {
    private UUID id;
    private String name;
    private String description;
    private String audioUrl;
    private AuscultationPointModel auscultationPoint;

    public ScenarioModelBuilder id(UUID id) {
      this.id = id;
      return this;
    }

    public ScenarioModelBuilder name(String name) {
      this.name = name;
      return this;
    }

    public ScenarioModelBuilder description(String description) {
      this.description = description;
      return this;
    }

    public ScenarioModelBuilder audioUrl(String audioUrl) {
      this.audioUrl = audioUrl;
      return this;
    }

    public ScenarioModelBuilder auscultationPoint(AuscultationPointModel auscultationPoint) {
      this.auscultationPoint = auscultationPoint;
      return this;
    }

    public ScenarioModel build() {
      ScenarioModel scenario = new ScenarioModel();
      scenario.setId(this.id);
      scenario.setName(this.name);
      scenario.setDescription(this.description);
      scenario.setAudioUrl(this.audioUrl);
      scenario.setAuscultationPoint(this.auscultationPoint);

      return scenario;
    }
  }
}
