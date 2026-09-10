package com.n0hana.echoes_server.auscultation;

import java.util.UUID;

import com.n0hana.echoes_server.animal.AnimalModel;

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
@Table(name = "auscultation_point_tb")
public class AuscultationPointModel {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  private String position;

  @ManyToOne
  @JoinColumn(name = "animal")
  private AnimalModel animal;

  public static AuscultationPointModelBuilder builder() {
    return new AuscultationPointModelBuilder();
  }

  public static class AuscultationPointModelBuilder {
    private UUID id;
    private String position;
    private AnimalModel animal;

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

    public AuscultationPointModel build() {
      AuscultationPointModel point = new AuscultationPointModel();
      point.setId(this.id);
      point.setPosition(this.position);
      point.setAnimal(this.animal);

      return point;
    }
  }

}
