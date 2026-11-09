package com.n0hana.echoes_server.auscultation;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.n0hana.echoes_server.animal.AnimalModel;
import com.n0hana.echoes_server.animal.AnimalService;
import com.n0hana.echoes_server.auscultation.exception.AuscultationPotionNotFound;
import com.n0hana.echoes_server.device.DeviceModel;
import com.n0hana.echoes_server.infra.logs.Auditable;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuscultationPointService {

    private final AuscultationPointRepository pointRepository;

    private final AnimalService animalService;

    public List<AuscultationPointModel> findAllPointsByAnimal(int page, int size, UUID animalId) {
        return pointRepository
                .findAuscultationPointsByAnimalId(
                        animalId,
                        PageRequest.of(
                                page,
                                size,
                                Sort.by("position")))
                .toList();
    }

    /**
     * Busca os pontos do animal associado ao dispositivo autenticado. Um
     * dispositivo sem animal associado não enxerga nenhum ponto.
     */
    public List<AuscultationPointModel> findAllPointsByDevice(int page, int size, DeviceModel device) {
        if (device == null || device.getAnimal() == null)
            return List.of();

        return findAllPointsByAnimal(page, size, device.getAnimal().getId());
    }

    public AuscultationPointModel findPointById(UUID id) {
        return pointRepository.findById(id)
                .orElseThrow(() -> new AuscultationPotionNotFound());
    }

    /**
     * Busca o ponto garantindo que, quando a requisição parte de um dispositivo,
     * ele pertence ao animal associado ao dispositivo.
     */
    public AuscultationPointModel findPointById(UUID id, DeviceModel device) {
        AuscultationPointModel point = this.findPointById(id);
        assertOwnedBy(point, device);
        return point;
    }

    @Transactional
    @Auditable(action = "CREATE", entity = "Point")
    public void newAuscultationPoint(AuscultationPointModel point, DeviceModel device) {
        UUID animalId;

        if (device != null) {
            if (device.getAnimal() == null)
                throw new AccessDeniedException("Dispositivo sem animal associado");

            animalId = device.getAnimal().getId();
        } else {
            animalId = point.getAnimal() != null
                    ? point.getAnimal().getId()
                    : null;
        }

        if (animalId == null)
            throw new IllegalArgumentException("Animal é obrigatório");

        AnimalModel animal = animalService.findAnimalById(animalId);

        point.setAnimal(animal);
        pointRepository.save(point);
    }

    @Transactional
    @Auditable(action = "UPDATE", entity = "Point")
    public void updateAuscultationPoint(UUID id, AuscultationPointModel point, DeviceModel device) {
        AuscultationPointModel savedPoint = this.findPointById(id, device);

        if (point.getPosition() != null && !Objects.equals(savedPoint.getPosition(), point.getPosition()))
            savedPoint.setPosition(point.getPosition());

        pointRepository.save(savedPoint);
    }

    @Transactional
    @Auditable(action = "DELETE", entity = "Point")
    public void deleteAuscultationPoint(UUID id, DeviceModel device) {
        AuscultationPointModel point = this.findPointById(id, device);

        pointRepository.delete(point);
    }

    // Métodos auxiliares
    public void auscultationPointExists(UUID id) {
        pointRepository
                .findById(id)
                .orElseThrow(() -> new AuscultationPotionNotFound());
    }

    private void assertOwnedBy(AuscultationPointModel point, DeviceModel device) {
        if (device == null)
            return;

        boolean belongsToDevice = device.getAnimal() != null
                && point.getAnimal() != null
                && device.getAnimal().getId().equals(point.getAnimal().getId());

        if (!belongsToDevice)
            throw new AccessDeniedException("Ponto de ausculta não pertence ao dispositivo autenticado");
    }
}
