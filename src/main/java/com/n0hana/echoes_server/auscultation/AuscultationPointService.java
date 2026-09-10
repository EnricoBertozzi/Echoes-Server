package com.n0hana.echoes_server.auscultation;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.n0hana.echoes_server.animal.AnimalModel;
import com.n0hana.echoes_server.animal.AnimalService;
import com.n0hana.echoes_server.auscultation.exception.AuscultationPotionNotFound;

import jakarta.transaction.Transactional;

@Service
public class AuscultationPointService {

  @Autowired
  private AuscultationPointRepository pointRepository;

  @Autowired 
  private AnimalService animalService;

  public List<AuscultationPointModel> findAllPointsByAnimal(int page, int size, UUID animalId) {
    return pointRepository
        .findPointsByAnimalId(
          animalId,
          PageRequest.of(
            page,
            size,
            Sort.by("position")))
        .toList();
  }

  public AuscultationPointModel findPointById(UUID id) {
    return pointRepository.findById(id)
        .orElseThrow(() -> new AuscultationPotionNotFound());
  }

  @Transactional
  public void newAuscultationPoint(AuscultationPointModel point) {
    AnimalModel animal = animalService.findAnimalById(point.getAnimal().getId());

    point.setAnimal(animal);
    pointRepository.save(point);
  }

  @Transactional
  public void updateAuscultationPoint(UUID id, AuscultationPointModel point) {
    AuscultationPointModel savedPoint = this.findPointById(id);

    if (!savedPoint.getPosition().equals(point.getPosition()))
      savedPoint.setPosition(point.getPosition());


    pointRepository.save(savedPoint);
  }

  @Transactional
  public void deleteAuscultationPoint(UUID id) {
    this.auscultationPointExists(id);

    pointRepository.deleteById(id);
  }

  // Métodos auxiliares
  public void auscultationPointExists(UUID id) {
    pointRepository
        .findById(id)
        .orElseThrow(() -> new AuscultationPotionNotFound());
  }
}
