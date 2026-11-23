package com.n0hana.echoes_server.animal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

import com.n0hana.echoes_server.animal.AnimalDTO.AnimalInfo;
import com.n0hana.echoes_server.animal.AnimalDTO.AnimalRegister;
import com.n0hana.echoes_server.animal.AnimalDTO.AnimalUpdate;
import com.n0hana.echoes_server.animal.exception.AnimalExceptionHandler;
import com.n0hana.echoes_server.animal.exception.AnimalInUseException;
import com.n0hana.echoes_server.animal.exception.AnimalNotFoundException;

@ExtendWith(MockitoExtension.class)
class AnimalControllerCoverageTests {

    @Mock
    private AnimalService animalService;

    @InjectMocks
    private AnimalController controller;

    private AnimalModel animal() {
        return AnimalModel.builder().id(UUID.randomUUID()).name("Gato").description("Felino").model("Felino").build();
    }

    @Test
    @DisplayName("POST salva o animal mapeado do DTO e devolve o AnimalInfo")
    void shouldSave() {
        AnimalModel saved = animal();
        when(animalService.save(any(AnimalModel.class))).thenReturn(saved);

        ResponseEntity<AnimalInfo> response = controller.save(new AnimalRegister("Gato", "Felino", "Felino"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(new AnimalInfo(saved.getId(), "Gato", "Felino", "Felino", true), response.getBody());
        ArgumentCaptor<AnimalModel> captor = ArgumentCaptor.forClass(AnimalModel.class);
        verify(animalService).save(captor.capture());
        assertEquals("Gato", captor.getValue().getName());
        assertEquals("Felino", captor.getValue().getModel());
    }

    @Test
    @DisplayName("GET lista animais mapeados")
    void shouldFindAll() {
        AnimalModel a = animal();
        when(animalService.findAllAnimals(0, 10)).thenReturn(List.of(a));

        ResponseEntity<List<AnimalInfo>> response = controller.findAllAnimals(0, 10);

        assertEquals(1, response.getBody().size());
        assertEquals(a.getId(), response.getBody().get(0).id());
    }

    @Test
    @DisplayName("GET /search filtra por nome")
    void shouldSearch() {
        AnimalModel a = animal();
        when(animalService.findAnimalsByName("Ga", 0, 10)).thenReturn(List.of(a));

        assertEquals("Gato", controller.findAnimalsByName("Ga", 0, 10).getBody().get(0).name());
    }

    @Test
    @DisplayName("GET /{id} devolve o animal")
    void shouldFindById() {
        AnimalModel a = animal();
        when(animalService.findAnimalById(a.getId())).thenReturn(a);

        ResponseEntity<AnimalInfo> response = controller.findAnimalById(a.getId());

        assertEquals(a.getId(), response.getBody().id());
        assertTrue(response.getBody().active());
    }

    @Test
    @DisplayName("PATCH atualiza com o modelo do DTO e responde 200")
    void shouldUpdate() {
        UUID id = UUID.randomUUID();

        ResponseEntity<Void> response = controller.updateAnimal(id, new AnimalUpdate("N", "D", "M"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ArgumentCaptor<AnimalModel> captor = ArgumentCaptor.forClass(AnimalModel.class);
        verify(animalService).updateAnimal(eq(id), captor.capture());
        assertEquals("N", captor.getValue().getName());
        assertEquals("D", captor.getValue().getDescription());
        assertEquals("M", captor.getValue().getModel());
    }

    @Test
    @DisplayName("Desativar e ativar devolvem o animal atualizado")
    void shouldDeactivateAndActivate() {
        AnimalModel a = animal();
        when(animalService.deactivateAnimal(a.getId())).thenReturn(a);
        when(animalService.activateAnimal(a.getId())).thenReturn(a);

        assertEquals(a.getId(), controller.deactivateAnimal(a.getId()).getBody().id());
        assertEquals(a.getId(), controller.activateAnimal(a.getId()).getBody().id());
    }

    @Test
    @DisplayName("DELETE responde 200 e delega ao serviço")
    void shouldDelete() {
        UUID id = UUID.randomUUID();

        ResponseEntity<Void> response = controller.deleteAnimal(id);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(animalService).deleteAnimal(id);
    }

    @Test
    @DisplayName("Handler traduz NotFound em 404 e InUse em 409 com a mensagem")
    void exceptionHandler() {
        AnimalExceptionHandler handler = new AnimalExceptionHandler();

        AnimalNotFoundException nf = new AnimalNotFoundException();
        ResponseEntity<Map<String, String>> r404 = handler.handleNotFound(nf);
        assertEquals(HttpStatus.NOT_FOUND, r404.getStatusCode());
        assertEquals(nf.getMessage(), r404.getBody().get("message"));

        AnimalInUseException iu = new AnimalInUseException();
        ResponseEntity<Map<String, String>> r409 = handler.handleInUse(iu);
        assertEquals(HttpStatus.CONFLICT, r409.getStatusCode());
        assertEquals(iu.getMessage(), r409.getBody().get("message"));
    }
}
