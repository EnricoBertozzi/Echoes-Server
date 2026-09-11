package com.n0hana.echoes_server.auscultation;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/points")
public class AuscultationPointController {

  @Autowired
  private AuscultationPointService pointService;

  @PostMapping
  public ResponseEntity<Void> newPoint(@RequestBody AuscultationPointDTO.PointRegister dto) {
    pointService.newAuscultationPoint(dto.toModel());
    return ResponseEntity.status(HttpStatus.CREATED).build();
  }

  @GetMapping
  public ResponseEntity<List<AuscultationPointDTO.PointInfo>> findAllPointsByAnimal(@RequestParam int page, @RequestParam int size, @RequestParam UUID animalId) {
    List<AuscultationPointDTO.PointInfo> list = pointService
        .findAllPointsByAnimal(page, size, animalId)
        .stream()
        .map(AuscultationPointDTO.PointInfo::from)
        .toList();
    return ResponseEntity.ok(list);
  }

  @GetMapping("/{id}")
  public ResponseEntity<AuscultationPointDTO.PointInfo> findPointById(@PathVariable("id") UUID id) {
    return ResponseEntity.ok(
      AuscultationPointDTO.PointInfo.from(pointService.findPointById(id))
    );
  }

  @PatchMapping("/{id}")
  public ResponseEntity<Void> updatePoint(@PathVariable UUID id, @RequestBody AuscultationPointDTO.PointUpdate dto) {
    pointService.updateAuscultationPoint(id, dto.toModel());

    return ResponseEntity.ok().build();
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deletePoint(@PathVariable UUID id) {
    pointService.deleteAuscultationPoint(id);

    return ResponseEntity.ok().build();
  }
}
