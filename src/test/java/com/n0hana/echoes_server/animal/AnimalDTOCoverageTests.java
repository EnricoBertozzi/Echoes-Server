package com.n0hana.echoes_server.animal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.n0hana.echoes_server.animal.AnimalDTO.AnimalInfo;
import com.n0hana.echoes_server.animal.AnimalDTO.AnimalRegister;
import com.n0hana.echoes_server.animal.AnimalDTO.AnimalUpdate;
import com.n0hana.echoes_server.auscultation.AuscultationPointDTO;
import com.n0hana.echoes_server.scenario.ScenarioDTO;

class AnimalDTOCoverageTests {

    @Test
    @DisplayName("AnimalRegister e AnimalUpdate mapeiam nome, descrição e modelo")
    void toModel() {
        AnimalModel r = new AnimalRegister("Gato", "Felino", "Felino").toModel();
        AnimalModel u = new AnimalUpdate("Cão", "Canino", "Canino").toModel();

        assertEquals("Gato", r.getName());
        assertEquals("Felino", r.getDescription());
        assertEquals("Felino", r.getModel());
        assertEquals("Cão", u.getName());
        assertEquals("Canino", u.getDescription());
        assertEquals("Canino", u.getModel());
    }

    @Test
    @DisplayName("AnimalInfo.from copia todos os campos, inclusive inativo")
    void infoFrom() {
        UUID id = UUID.randomUUID();
        AnimalModel m = AnimalModel.builder().id(id).name("Gato").description("d").model("m").build();
        m.setActive(false);

        assertEquals(new AnimalInfo(id, "Gato", "d", "m", false), AnimalInfo.from(m));
    }

    @Test
    @DisplayName("Classes contêineres de DTO são instanciáveis")
    void containers() {
        assertNotNull(new AnimalDTO());
        assertNotNull(new ScenarioDTO());
        assertNotNull(new AuscultationPointDTO());
    }

    @Test
    @DisplayName("AnimalService.save devolve o animal persistido pelo repositório")
    void saveDelegates() {
        AnimalRepository repo = mock(AnimalRepository.class);
        AnimalService service = new AnimalService(repo, null, null);
        AnimalModel in = AnimalModel.builder().name("Gato").build();
        AnimalModel out = AnimalModel.builder().id(UUID.randomUUID()).name("Gato").build();
        when(repo.save(in)).thenReturn(out);

        assertSame(out, service.save(in));
    }
}
