package com.n0hana.echoes_server.auscultation;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.n0hana.echoes_server.animal.AnimalModel;
import com.n0hana.echoes_server.infra.security.SecurityConfig;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/points")
@SecurityRequirement(name = SecurityConfig.SECURITY)
public class AuscultationPointController {

  @Autowired
  private AuscultationPointService pointService;

  @PostMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'DEVICE')")
  public ResponseEntity<Void> newPoint(@RequestBody AuscultationPointDTO.PointRegister dto,
      @AuthenticationPrincipal AnimalModel device) {
    pointService.newAuscultationPoint(dto.toModel(), device);
    return ResponseEntity.status(HttpStatus.CREATED).build();
  }

  @GetMapping
  @PreAuthorize("!hasRole('DEVICE')")
  public ResponseEntity<List<AuscultationPointDTO.PointInfo>> findAllPointsByAnimal(@RequestParam int page, @RequestParam int size, @RequestParam UUID animalId) {
    List<AuscultationPointDTO.PointInfo> list = pointService
        .findAllPointsByAnimal(page, size, animalId)
        .stream()
        .map(AuscultationPointDTO.PointInfo::from)
        .toList();
    return ResponseEntity.ok(list);
  }

  /**
   * Lista os pontos do animal autenticado pelo token do dispositivo.
   */
  @GetMapping("/me")
  @PreAuthorize("hasRole('DEVICE')")
  public ResponseEntity<List<AuscultationPointDTO.PointInfo>> findMyPoints(@RequestParam int page,
      @RequestParam int size,
      @AuthenticationPrincipal AnimalModel device) {
    List<AuscultationPointDTO.PointInfo> list = pointService
        .findAllPointsByAnimalMac(page, size, device.getMac())
        .stream()
        .map(AuscultationPointDTO.PointInfo::from)
        .toList();
    return ResponseEntity.ok(list);
  }

  @GetMapping("/{id}")
  public ResponseEntity<AuscultationPointDTO.PointInfo> findPointById(@PathVariable("id") UUID id,
      @AuthenticationPrincipal AnimalModel device) {
    return ResponseEntity.ok(
      AuscultationPointDTO.PointInfo.from(pointService.findPointById(id, device))
    );
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN', 'DEVICE')")
  public ResponseEntity<Void> updatePoint(@PathVariable UUID id, @RequestBody AuscultationPointDTO.PointUpdate dto,
      @AuthenticationPrincipal AnimalModel device) {
    pointService.updateAuscultationPoint(id, dto.toModel(), device);

    return ResponseEntity.ok().build();
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN', 'DEVICE')")
  public ResponseEntity<Void> deletePoint(@PathVariable UUID id,
      @AuthenticationPrincipal AnimalModel device) {
    pointService.deleteAuscultationPoint(id, device);
    return ResponseEntity.ok().build();
  }
}
