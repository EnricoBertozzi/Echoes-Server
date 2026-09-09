package com.n0hana.echoes_server.animal.model;

import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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

  private String model;

  public static AnimalModelBuilder builder() {
    return new AnimalModelBuilder();
  }

  public static class AnimalModelBuilder {
    private UUID id;
    private String name;
    private String description;
    private String model;

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

    public AnimalModelBuilder model(String model) {
      this.model = model;
      return this;
    }

    public AnimalModel build() {
      AnimalModel animal = new AnimalModel();
      animal.setId(this.id);
      animal.setDescription(this.description);
      animal.setName(this.name);
      animal.setModel(this.model);
      return animal;
    }
  }
}
