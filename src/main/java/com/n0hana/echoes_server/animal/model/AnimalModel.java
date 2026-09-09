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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "animal_tb")
public class AnimalModel {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  private String name;

  private String description;

  private String imageUrl;

  @OneToMany(mappedBy = "animal", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
  private List<AuscultationPointModel> auscultationPoints;

  public static AnimalModelBuilder builder() {
    return new AnimalModelBuilder();
  }

  public static class AnimalModelBuilder {
    private UUID id;
    private String name;
    private String description;
    private String imageUrl;
    private List<AuscultationPointModel> auscultationPoints;

    public AnimalModelBuilder id(UUID id) {
      this.id = id;
      return this;
    }

    public AnimalModelBuilder name(String name) {
      this.name = name;
      return this;
    }

    public AnimalModelBuilder description(String description) {
      this.description = description;
      return this;
    }

    public AnimalModelBuilder imageUrl(String imageUrl) {
      this.imageUrl = imageUrl;
      return this;
    }

    public AnimalModelBuilder auscultationPoints(List<AuscultationPointModel> auscultationPoints) {
      this.auscultationPoints = auscultationPoints;
      return this;
    }

    public AnimalModel build() {
      AnimalModel animal = new AnimalModel();
      animal.setId(this.id);
      animal.setDescription(this.description);
      animal.setName(this.name);
      animal.setImageUrl(this.imageUrl);
      animal.setAuscultationPoints(new ArrayList<>(this.auscultationPoints));

      animal.getAuscultationPoints().forEach(point -> point.setAnimal(animal));
      return animal;
    }
  }
}
