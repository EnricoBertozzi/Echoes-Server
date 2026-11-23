package com.n0hana.echoes_server.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.n0hana.echoes_server.auscultation.AuscultationPointModel;
import com.n0hana.echoes_server.scenario.ScenarioDTO.ScenarioInfo;
import com.n0hana.echoes_server.scenario.ScenarioDTO.ScenarioRegister;
import com.n0hana.echoes_server.scenario.ScenarioDTO.ScenarioUpdate;
import com.n0hana.echoes_server.scenario.exception.ScenarioExceptionHandler;
import com.n0hana.echoes_server.scenario.exception.ScenarioInUseException;
import com.n0hana.echoes_server.scenario.exception.ScenarioNotFoundException;

class ScenarioDTOCoverageTests {

    @Test
    @DisplayName("ScenarioRegister.toModel mapeia nome, descrição e ponto")
    void registerToModel() {
        UUID pointId = UUID.randomUUID();

        ScenarioModel model = new ScenarioRegister("Sopro", "Descrição", pointId).toModel();

        assertEquals("Sopro", model.getName());
        assertEquals("Descrição", model.getDescription());
        assertEquals(pointId, model.getAuscultationPoint().getId());
        assertTrue(model.isActive());
        assertNull(model.getAudioUrl());
    }

    @Test
    @DisplayName("ScenarioUpdate.toModel mapeia nome e descrição sem ponto")
    void updateToModel() {
        ScenarioModel model = new ScenarioUpdate("Novo", "Nova descrição").toModel();

        assertEquals("Novo", model.getName());
        assertEquals("Nova descrição", model.getDescription());
        assertNull(model.getAuscultationPoint());
    }

    @Test
    @DisplayName("ScenarioInfo.from com ponto copia o id do ponto e o estado ativo")
    void infoFromWithPoint() {
        UUID pointId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        ScenarioModel model = ScenarioModel.builder().id(id).name("A").description("D").audioUrl("a.mp3")
                .auscultationPoint(AuscultationPointModel.builder().id(pointId).build()).build();
        model.setActive(false);

        ScenarioInfo info = ScenarioInfo.from(model);

        assertEquals(new ScenarioInfo(id, "A", "D", "a.mp3", pointId, false), info);
        assertFalse(info.active());
    }

    @Test
    @DisplayName("ScenarioInfo.from sem ponto deixa pointId nulo")
    void infoFromWithoutPoint() {
        ScenarioInfo info = ScenarioInfo.from(ScenarioModel.builder().name("A").build());

        assertNull(info.pointId());
        assertTrue(info.active());
    }

    @Test
    @DisplayName("Builder de ScenarioModel preenche todos os campos")
    void builderFillsAll() {
        UUID id = UUID.randomUUID();
        ScenarioModel m = ScenarioModel.builder().id(id).name("n").description("d").audioUrl("u").build();

        assertEquals(id, m.getId());
        assertEquals("n", m.getName());
        assertEquals("d", m.getDescription());
        assertEquals("u", m.getAudioUrl());
    }

    @Test
    @DisplayName("Handler traduz NotFound em 404 e InUse em 409 com a mensagem")
    void exceptionHandler() {
        ScenarioExceptionHandler handler = new ScenarioExceptionHandler();

        ScenarioNotFoundException notFound = new ScenarioNotFoundException();
        ResponseEntity<Map<String, String>> r404 = handler.handleNotFound(notFound);
        assertEquals(HttpStatus.NOT_FOUND, r404.getStatusCode());
        assertEquals(notFound.getMessage(), r404.getBody().get("message"));

        ScenarioInUseException inUse = new ScenarioInUseException();
        ResponseEntity<Map<String, String>> r409 = handler.handleInUse(inUse);
        assertEquals(HttpStatus.CONFLICT, r409.getStatusCode());
        assertEquals(inUse.getMessage(), r409.getBody().get("message"));
    }
}
