package com.n0hana.echoes_server.auscultation;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.n0hana.echoes_server.animal.AnimalModel;
import com.n0hana.echoes_server.animal.AnimalService;
import com.n0hana.echoes_server.auscultation.exception.AuscultationPotionNotFound;
import com.n0hana.echoes_server.infra.logs.Auditable;

import jakarta.transaction.Transactional;

@Service
public class AuscultationPointService {

    @Autowired
    private AuscultationPointRepository pointRepository;

    @Autowired
    private AnimalService animalService;

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
     * Busca os pontos do animal identificado pelo MAC presente no token do
     * dispositivo.
     */
    public List<AuscultationPointModel> findAllPointsByAnimalMac(int page, int size, String mac) {
        AnimalModel animal = animalService.findAnimalByMac(mac);
        return findAllPointsByAnimal(page, size, animal.getId());
    }

    public AuscultationPointModel findPointById(UUID id) {
        return pointRepository.findById(id)
                .orElseThrow(() -> new AuscultationPotionNotFound());
    }

    /**
     * Busca o ponto garantindo que, quando a requisição parte de um dispositivo,
     * ele pertence ao animal autenticado.
     */
    public AuscultationPointModel findPointById(UUID id, AnimalModel device) {
        AuscultationPointModel point = this.findPointById(id);
        assertOwnedBy(point, device);
        return point;
    }

    @Transactional
    @Auditable(action = "CREATE", entity = "Point")
    public void newAuscultationPoint(AuscultationPointModel point, AnimalModel device) {
        UUID animalId = device != null
                ? device.getId()
                : point.getAnimal().getId();

        AnimalModel animal = animalService.findAnimalById(animalId);

        point.setAnimal(animal);
        pointRepository.save(point);
    }

    @Transactional
    @Auditable(action = "UPDATE", entity = "Point")
    public void updateAuscultationPoint(UUID id, AuscultationPointModel point, AnimalModel device) {
        AuscultationPointModel savedPoint = this.findPointById(id, device);

        if (point.getPosition() != null && !Objects.equals(savedPoint.getPosition(), point.getPosition()))
            savedPoint.setPosition(point.getPosition());

        pointRepository.save(savedPoint);
    }

    @Transactional
    @Auditable(action = "DELETE", entity = "Point")
    public void deleteAuscultationPoint(UUID id, AnimalModel device) {
        AuscultationPointModel point = this.findPointById(id, device);

        pointRepository.delete(point);
    }

    // Métodos auxiliares
    public void auscultationPointExists(UUID id) {
        pointRepository
                .findById(id)
                .orElseThrow(() -> new AuscultationPotionNotFound());
    }

    private void assertOwnedBy(AuscultationPointModel point, AnimalModel device) {
        if (device == null)
            return;

        boolean belongsToDevice = point.getAnimal() != null
                && device.getId().equals(point.getAnimal().getId());

        if (!belongsToDevice)
            throw new AccessDeniedException("Ponto de ausculta não pertence ao dispositivo autenticado");
    }
}
