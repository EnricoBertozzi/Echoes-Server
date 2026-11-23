package com.n0hana.echoes_server.animal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.n0hana.echoes_server.animal.exception.AnimalInUseException;
import com.n0hana.echoes_server.auscultation.AuscultationPointModel;
import com.n0hana.echoes_server.auscultation.AuscultationPointRepository;
import com.n0hana.echoes_server.auscultation.AuscultationPointService;
import com.n0hana.echoes_server.auscultation.exception.AuscultationPointInUseException;
import com.n0hana.echoes_server.device.DeviceModel;
import com.n0hana.echoes_server.device.DeviceRepository;
import com.n0hana.echoes_server.device.DeviceScenarioModel;
import com.n0hana.echoes_server.device.DeviceScenarioRepository;
import com.n0hana.echoes_server.scenario.ScenarioModel;
import com.n0hana.echoes_server.scenario.ScenarioRepository;
import com.n0hana.echoes_server.scenario.ScenarioService;
import com.n0hana.echoes_server.scenario.exception.ScenarioInUseException;

import jakarta.persistence.EntityManager;

/**
 * Regras de desativação e remoção do catálogo (animal, ponto e cenário) contra
 * o banco de testes: a coluna {@code active} nasce verdadeira e as consultas de
 * associação com dispositivos enxergam os vínculos reais.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CatalogDeactivationIntegrationTest {

  @Autowired private AnimalService animalService;
  @Autowired private AuscultationPointService pointService;
  @Autowired private ScenarioService scenarioService;

  @Autowired private AnimalRepository animalRepository;
  @Autowired private AuscultationPointRepository pointRepository;
  @Autowired private ScenarioRepository scenarioRepository;
  @Autowired private DeviceRepository deviceRepository;
  @Autowired private DeviceScenarioRepository deviceScenarioRepository;
  @Autowired private EntityManager entityManager;

  private AnimalModel animal;
  private AuscultationPointModel point;
  private ScenarioModel scenario;

  @BeforeEach
  void setUp() {
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));

    animal = animalRepository.saveAndFlush(AnimalModel.builder().name("Cachorro").build());
    point = pointRepository.saveAndFlush(AuscultationPointModel.builder().position("Aorta").animal(animal).build());
    scenario = scenarioRepository.saveAndFlush(ScenarioModel.builder()
        .name("Sopro").description("Sopro mitral").audioUrl("catalogo-inexistente.mp3")
        .auscultationPoint(point).build());
  }

  @AfterEach
  void tearDown() {
    RequestContextHolder.resetRequestAttributes();
  }

  private DeviceModel device(String hardwareId, AnimalModel animal) {
    return deviceRepository.saveAndFlush(DeviceModel.builder()
        .name("Simulador " + hardwareId)
        .hardwareId(hardwareId)
        .publicKey("pem")
        .animal(animal)
        .build());
  }

  @Test
  @DisplayName("Animal, ponto e cenário são gravados como ativos")
  void catalogStartsActive() {
    entityManager.clear();

    assertTrue(animalRepository.findById(animal.getId()).orElseThrow().isActive());
    assertTrue(pointRepository.findById(point.getId()).orElseThrow().isActive());
    assertTrue(scenarioRepository.findById(scenario.getId()).orElseThrow().isActive());
  }

  @Test
  @DisplayName("Com dispositivo usando o animal, nada do catálogo dele é removido")
  void deviceWithAnimalBlocksAnimalAndPointRemoval() {
    device("CA:7A:10:60:00:01", animal);

    assertThrows(AnimalInUseException.class, () -> animalService.deleteAnimal(animal.getId()));
    assertThrows(AuscultationPointInUseException.class,
        () -> pointService.deleteAuscultationPoint(point.getId(), null));

    assertTrue(animalRepository.existsById(animal.getId()));
    assertTrue(pointRepository.existsById(point.getId()));
  }

  @Test
  @DisplayName("Cenário configurado em um dispositivo não é removido")
  void configuredScenarioBlocksRemoval() {
    DeviceModel device = device("CA:7A:10:60:00:02", animal);
    deviceScenarioRepository.saveAndFlush(DeviceScenarioModel.builder()
        .device(device).point(point).scenario(scenario).build());

    assertThrows(ScenarioInUseException.class, () -> scenarioService.deleteScenario(scenario.getId()));
    assertTrue(scenarioRepository.existsById(scenario.getId()));
  }

  @Test
  @DisplayName("Associado a dispositivo, o catálogo pode ser desativado")
  void inUseCatalogCanBeDeactivated() {
    device("CA:7A:10:60:00:03", animal);

    animalService.deactivateAnimal(animal.getId());
    pointService.deactivateAuscultationPoint(point.getId());
    scenarioService.deactivateScenario(scenario.getId());
    entityManager.flush();
    entityManager.clear();

    assertFalse(animalRepository.findById(animal.getId()).orElseThrow().isActive());
    assertFalse(pointRepository.findById(point.getId()).orElseThrow().isActive());
    assertFalse(scenarioRepository.findById(scenario.getId()).orElseThrow().isActive());
  }

  @Test
  @DisplayName("Sem dispositivos associados, o animal é removido com pontos e cenários")
  void unusedAnimalIsRemoved() {
    // como numa requisição nova: o animal é lido do banco já com os pontos
    entityManager.clear();

    animalService.deleteAnimal(animal.getId());
    entityManager.flush();
    entityManager.clear();

    assertFalse(animalRepository.existsById(animal.getId()));
    assertFalse(pointRepository.existsById(point.getId()));
    assertFalse(scenarioRepository.existsById(scenario.getId()));
  }
}
