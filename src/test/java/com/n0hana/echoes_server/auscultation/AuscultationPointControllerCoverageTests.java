package com.n0hana.echoes_server.auscultation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.n0hana.echoes_server.animal.AnimalModel;
import com.n0hana.echoes_server.auscultation.AuscultationPointDTO.PointInfo;
import com.n0hana.echoes_server.auscultation.AuscultationPointDTO.PointRegister;
import com.n0hana.echoes_server.auscultation.AuscultationPointDTO.PointUpdate;
import com.n0hana.echoes_server.auscultation.exception.AuscultationExceptionHandler;
import com.n0hana.echoes_server.auscultation.exception.AuscultationPointInUseException;
import com.n0hana.echoes_server.auscultation.exception.AuscultationPointNotFound;
import com.n0hana.echoes_server.device.DeviceModel;

@ExtendWith(MockitoExtension.class)
class AuscultationPointControllerCoverageTests {

    @Mock
    private AuscultationPointService pointService;

    @InjectMocks
    private AuscultationPointController controller;

    private AuscultationPointModel point(UUID animalId) {
        return AuscultationPointModel.builder().id(UUID.randomUUID()).position("Mitral")
                .animal(AnimalModel.builder().id(animalId).build()).build();
    }

    @Test
    @DisplayName("POST cria o ponto mapeando o DTO e responde 201")
    void shouldCreate() {
        UUID animalId = UUID.randomUUID();
        DeviceModel device = new DeviceModel();

        ResponseEntity<Void> response = controller.newPoint(new PointRegister("Mitral", animalId), device);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        ArgumentCaptor<AuscultationPointModel> captor = ArgumentCaptor.forClass(AuscultationPointModel.class);
        verify(pointService).newAuscultationPoint(captor.capture(), any(DeviceModel.class));
        assertEquals("Mitral", captor.getValue().getPosition());
        assertEquals(animalId, captor.getValue().getAnimal().getId());
    }

    @Test
    @DisplayName("GET por animal devolve a lista mapeada")
    void shouldFindByAnimal() {
        UUID animalId = UUID.randomUUID();
        AuscultationPointModel p = point(animalId);
        when(pointService.findAllPointsByAnimal(0, 10, animalId)).thenReturn(List.of(p));

        ResponseEntity<List<PointInfo>> response = controller.findAllPointsByAnimal(0, 10, animalId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(List.of(new PointInfo(p.getId(), "Mitral", animalId, true)), response.getBody());
    }

    @Test
    @DisplayName("GET /me devolve os pontos do dispositivo")
    void shouldFindMine() {
        UUID animalId = UUID.randomUUID();
        DeviceModel device = new DeviceModel();
        when(pointService.findAllPointsByDevice(0, 10, device)).thenReturn(List.of(point(animalId)));

        ResponseEntity<List<PointInfo>> response = controller.findMyPoints(0, 10, device);

        assertEquals(1, response.getBody().size());
        assertEquals(animalId, response.getBody().get(0).animalId());
    }

    @Test
    @DisplayName("GET /{id} devolve o ponto")
    void shouldFindById() {
        AuscultationPointModel p = point(UUID.randomUUID());
        when(pointService.findPointById(p.getId(), null)).thenReturn(p);

        ResponseEntity<PointInfo> response = controller.findPointById(p.getId(), null);

        assertEquals(p.getId(), response.getBody().id());
        assertTrue(response.getBody().active());
    }

    @Test
    @DisplayName("PATCH atualiza com a posição do DTO e responde 200")
    void shouldUpdate() {
        UUID id = UUID.randomUUID();

        ResponseEntity<Void> response = controller.updatePoint(id, new PointUpdate("Nova"), null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ArgumentCaptor<AuscultationPointModel> captor = ArgumentCaptor.forClass(AuscultationPointModel.class);
        verify(pointService).updateAuscultationPoint(org.mockito.ArgumentMatchers.eq(id), captor.capture(), any());
        assertEquals("Nova", captor.getValue().getPosition());
        assertNull(captor.getValue().getAnimal());
    }

    @Test
    @DisplayName("Desativar e ativar devolvem o ponto atualizado")
    void shouldDeactivateAndActivate() {
        AuscultationPointModel p = point(UUID.randomUUID());
        when(pointService.deactivateAuscultationPoint(p.getId())).thenReturn(p);
        when(pointService.activateAuscultationPoint(p.getId())).thenReturn(p);

        assertEquals(p.getId(), controller.deactivatePoint(p.getId()).getBody().id());
        assertEquals(p.getId(), controller.activatePoint(p.getId()).getBody().id());
    }

    @Test
    @DisplayName("DELETE responde 200 e delega ao serviço")
    void shouldDelete() {
        UUID id = UUID.randomUUID();
        DeviceModel device = new DeviceModel();

        ResponseEntity<Void> response = controller.deletePoint(id, device);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(pointService).deleteAuscultationPoint(id, device);
    }

    @Test
    @DisplayName("DTOs: PointUpdate/PointRegister.toModel e handler de exceções")
    void dtosAndHandler() {
        UUID animalId = UUID.randomUUID();
        assertEquals("p", new PointUpdate("p").toModel().getPosition());
        AuscultationPointModel m = new PointRegister("p", animalId).toModel();
        assertEquals("p", m.getPosition());
        assertEquals(animalId, m.getAnimal().getId());

        AuscultationExceptionHandler handler = new AuscultationExceptionHandler();
        AuscultationPointNotFound nf = new AuscultationPointNotFound();
        ResponseEntity<Map<String, String>> r404 = handler.handleNotFound(nf);
        assertEquals(HttpStatus.NOT_FOUND, r404.getStatusCode());
        assertEquals(nf.getMessage(), r404.getBody().get("message"));

        AuscultationPointInUseException iu = new AuscultationPointInUseException();
        ResponseEntity<Map<String, String>> r409 = handler.handleInUse(iu);
        assertEquals(HttpStatus.CONFLICT, r409.getStatusCode());
        assertEquals(iu.getMessage(), r409.getBody().get("message"));
    }
}
