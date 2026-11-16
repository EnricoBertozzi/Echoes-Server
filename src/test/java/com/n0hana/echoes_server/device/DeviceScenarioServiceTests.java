package com.n0hana.echoes_server.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.n0hana.echoes_server.animal.AnimalModel;
import com.n0hana.echoes_server.auscultation.AuscultationPointModel;
import com.n0hana.echoes_server.auscultation.AuscultationPointRepository;
import com.n0hana.echoes_server.auscultation.exception.AuscultationPointNotFound;
import com.n0hana.echoes_server.device.DeviceScenarioDTO.DeviceConfig;
import com.n0hana.echoes_server.device.exception.DeviceNotFoundException;
import com.n0hana.echoes_server.device.exception.InvalidDeviceUpdateException;
import com.n0hana.echoes_server.device.port.DeviceScopeResolver;
import com.n0hana.echoes_server.institution.InstitutionModel;
import com.n0hana.echoes_server.scenario.ScenarioModel;
import com.n0hana.echoes_server.scenario.ScenarioRepository;
import com.n0hana.echoes_server.scenario.exception.ScenarioNotFoundException;
import com.n0hana.echoes_server.user.model.Teacher;
import com.n0hana.echoes_server.user.model.User;

@ExtendWith(MockitoExtension.class)
class DeviceScenarioServiceTests {

  @Mock
  private DeviceRepository deviceRepository;

  @Mock
  private AuscultationPointRepository pointRepository;

  @Mock
  private ScenarioRepository scenarioRepository;

  @Mock
  private DeviceScenarioRepository repository;

  @Mock
  private DeviceScopeResolver scopeResolver;

  @InjectMocks
  private DeviceScenarioService service;

  private InstitutionModel institution;
  private AnimalModel animal;
  private DeviceModel device;
  private AuscultationPointModel point;
  private ScenarioModel scenario;
  private User teacher;

  @BeforeEach
  void setUp() {
    institution = InstitutionModel.builder().id(UUID.randomUUID()).build();
    animal = AnimalModel.builder().id(UUID.randomUUID()).name("Cachorro").build();
    device = DeviceModel.builder().id(UUID.randomUUID()).institution(institution).animal(animal).build();
    point = AuscultationPointModel.builder().id(UUID.randomUUID()).position("Aorta").animal(animal).build();
    scenario = ScenarioModel.builder().id(UUID.randomUUID()).name("Sopro").description("Sopro mitral")
        .audioUrl("sopro.mp3").auscultationPoint(point).build();
    teacher = new Teacher();
    teacher.setId(UUID.randomUUID());
  }

  // leitura pelo ESP

  @Test
  @DisplayName("Devolve os pontos do animal com o cenário atual e a URI do áudio")
  void returnsPointsWithCurrentScenario() {
    AuscultationPointModel other = AuscultationPointModel.builder().id(UUID.randomUUID()).position("Pulmão")
        .animal(animal).build();
    DeviceScenarioModel selection = DeviceScenarioModel.builder().device(device).point(point).scenario(scenario)
        .build();
    when(repository.findByDeviceId(device.getId())).thenReturn(List.of(selection));
    when(pointRepository.findByAnimalIdOrderByPositionAsc(animal.getId())).thenReturn(List.of(point, other));

    DeviceConfig result = service.findConfig(device);

    assertEquals(device.getId(), result.deviceId());
    assertEquals(2, result.points().size());
    assertEquals("Aorta", result.points().get(0).position());
    assertEquals(scenario.getId(), result.points().get(0).scenario().id());
    assertEquals("Sopro", result.points().get(0).scenario().name());
    assertEquals("/api/v1/scenarios/" + scenario.getId() + "/audio", result.points().get(0).scenario().audioUri());
    assertEquals("Pulmão", result.points().get(1).position());
    assertNull(result.points().get(1).scenario());
  }

  @Test
  @DisplayName("Dispositivo sem animal devolve lista de pontos vazia")
  void returnsEmptyPointsWithoutAnimal() {
    device.setAnimal(null);

    DeviceConfig result = service.findConfig(device);

    assertEquals(device.getId(), result.deviceId());
    assertEquals(0, result.points().size());
    verifyNoInteractions(pointRepository, repository);
  }

  // leitura pelo painel

  @Test
  @DisplayName("Gestor ou professor da instituição lê a configuração do dispositivo")
  void institutionUserReadsConfig() {
    when(deviceRepository.findById(device.getId())).thenReturn(Optional.of(device));
    when(scopeResolver.resolve(teacher)).thenReturn(new DeviceScope.InstitutionScope(institution.getId()));
    when(repository.findByDeviceId(device.getId())).thenReturn(List.of());
    when(pointRepository.findByAnimalIdOrderByPositionAsc(animal.getId())).thenReturn(List.of(point));

    DeviceConfig result = service.findConfig(teacher, device.getId());

    assertEquals(1, result.points().size());
    assertNull(result.points().get(0).scenario());
  }

  @Test
  @DisplayName("Admin lê a configuração de qualquer dispositivo")
  void adminReadsAnyConfig() {
    when(deviceRepository.findById(device.getId())).thenReturn(Optional.of(device));
    when(scopeResolver.resolve(teacher)).thenReturn(new DeviceScope.All());
    when(repository.findByDeviceId(device.getId())).thenReturn(List.of());
    when(pointRepository.findByAnimalIdOrderByPositionAsc(animal.getId())).thenReturn(List.of());

    assertEquals(device.getId(), service.findConfig(teacher, device.getId()).deviceId());
  }

  @Test
  @DisplayName("Usuário de outra instituição não lê a configuração")
  void otherInstitutionCannotReadConfig() {
    when(deviceRepository.findById(device.getId())).thenReturn(Optional.of(device));
    when(scopeResolver.resolve(teacher)).thenReturn(new DeviceScope.InstitutionScope(UUID.randomUUID()));

    assertThrows(AccessDeniedException.class, () -> service.findConfig(teacher, device.getId()));

    verifyNoInteractions(pointRepository, repository);
  }

  @Test
  @DisplayName("Dispositivo sem instituição não é lido por gestor ou professor")
  void deviceWithoutInstitutionIsDenied() {
    device.setInstitution(null);
    when(deviceRepository.findById(device.getId())).thenReturn(Optional.of(device));
    when(scopeResolver.resolve(teacher)).thenReturn(new DeviceScope.InstitutionScope(institution.getId()));

    assertThrows(AccessDeniedException.class, () -> service.findConfig(teacher, device.getId()));
  }

  @Test
  @DisplayName("Dispositivo que não existe responde não encontrado")
  void missingDeviceIsNotFound() {
    UUID id = UUID.randomUUID();
    when(deviceRepository.findById(id)).thenReturn(Optional.empty());

    assertThrows(DeviceNotFoundException.class, () -> service.findConfig(teacher, id));
  }

  // escolher o cenário

  @Test
  @DisplayName("Escolhe o cenário de um ponto que ainda não tinha escolha")
  void createsSelection() {
    mockAccess();
    when(pointRepository.findById(point.getId())).thenReturn(Optional.of(point));
    when(scenarioRepository.findById(scenario.getId())).thenReturn(Optional.of(scenario));
    when(repository.findByDeviceIdAndPointId(device.getId(), point.getId())).thenReturn(Optional.empty());
    when(repository.save(any(DeviceScenarioModel.class))).thenAnswer(i -> i.getArgument(0));

    DeviceScenarioModel result = service.select(teacher, device.getId(), point.getId(), scenario.getId());

    ArgumentCaptor<DeviceScenarioModel> saved = ArgumentCaptor.forClass(DeviceScenarioModel.class);
    verify(repository).save(saved.capture());
    assertSame(device, saved.getValue().getDevice());
    assertSame(point, saved.getValue().getPoint());
    assertSame(scenario, result.getScenario());
  }

  @Test
  @DisplayName("Escolher de novo troca o cenário anterior em vez de criar outra linha")
  void replacesExistingSelection() {
    ScenarioModel previous = ScenarioModel.builder().id(UUID.randomUUID()).name("Antigo").auscultationPoint(point)
        .build();
    DeviceScenarioModel existing = DeviceScenarioModel.builder().id(UUID.randomUUID()).device(device).point(point)
        .scenario(previous).build();
    mockAccess();
    when(pointRepository.findById(point.getId())).thenReturn(Optional.of(point));
    when(scenarioRepository.findById(scenario.getId())).thenReturn(Optional.of(scenario));
    when(repository.findByDeviceIdAndPointId(device.getId(), point.getId())).thenReturn(Optional.of(existing));
    when(repository.save(existing)).thenReturn(existing);

    DeviceScenarioModel result = service.select(teacher, device.getId(), point.getId(), scenario.getId());

    assertSame(existing, result);
    assertSame(scenario, existing.getScenario());
  }

  @Test
  @DisplayName("Dispositivo sem animal não aceita escolha")
  void rejectsDeviceWithoutAnimal() {
    device.setAnimal(null);
    mockAccess();

    assertThrows(InvalidDeviceUpdateException.class,
        () -> service.select(teacher, device.getId(), point.getId(), scenario.getId()));

    verify(repository, never()).save(any());
  }

  @Test
  @DisplayName("Ponto que não existe responde não encontrado")
  void rejectsMissingPoint() {
    mockAccess();
    when(pointRepository.findById(point.getId())).thenReturn(Optional.empty());

    assertThrows(AuscultationPointNotFound.class,
        () -> service.select(teacher, device.getId(), point.getId(), scenario.getId()));
  }

  @Test
  @DisplayName("Ponto de outro animal é recusado")
  void rejectsPointOfOtherAnimal() {
    point.setAnimal(AnimalModel.builder().id(UUID.randomUUID()).build());
    mockAccess();
    when(pointRepository.findById(point.getId())).thenReturn(Optional.of(point));

    assertThrows(InvalidDeviceUpdateException.class,
        () -> service.select(teacher, device.getId(), point.getId(), scenario.getId()));

    verify(repository, never()).save(any());
  }

  @Test
  @DisplayName("Cenário que não existe responde não encontrado")
  void rejectsMissingScenario() {
    mockAccess();
    when(pointRepository.findById(point.getId())).thenReturn(Optional.of(point));
    when(scenarioRepository.findById(scenario.getId())).thenReturn(Optional.empty());

    assertThrows(ScenarioNotFoundException.class,
        () -> service.select(teacher, device.getId(), point.getId(), scenario.getId()));
  }

  @Test
  @DisplayName("Cenário de outro ponto é recusado")
  void rejectsScenarioOfOtherPoint() {
    scenario.setAuscultationPoint(AuscultationPointModel.builder().id(UUID.randomUUID()).animal(animal).build());
    mockAccess();
    when(pointRepository.findById(point.getId())).thenReturn(Optional.of(point));
    when(scenarioRepository.findById(scenario.getId())).thenReturn(Optional.of(scenario));

    assertThrows(InvalidDeviceUpdateException.class,
        () -> service.select(teacher, device.getId(), point.getId(), scenario.getId()));

    verify(repository, never()).save(any());
  }

  @Test
  @DisplayName("Usuário de outra instituição não escolhe o cenário")
  void otherInstitutionCannotSelect() {
    when(deviceRepository.findById(device.getId())).thenReturn(Optional.of(device));
    when(scopeResolver.resolve(teacher)).thenReturn(new DeviceScope.InstitutionScope(UUID.randomUUID()));

    assertThrows(AccessDeniedException.class,
        () -> service.select(teacher, device.getId(), point.getId(), scenario.getId()));

    verifyNoInteractions(pointRepository, scenarioRepository);
    verify(repository, never()).save(any());
  }

  @Test
  @DisplayName("Quem não tem escopo (aluno) não escolhe o cenário")
  void userWithoutScopeCannotSelect() {
    when(deviceRepository.findById(device.getId())).thenReturn(Optional.of(device));
    when(scopeResolver.resolve(teacher)).thenThrow(new AccessDeniedException("Sem acesso a dispositivos"));

    assertThrows(AccessDeniedException.class,
        () -> service.select(teacher, device.getId(), point.getId(), scenario.getId()));

    verify(repository, never()).save(any());
  }

  private void mockAccess() {
    when(deviceRepository.findById(device.getId())).thenReturn(Optional.of(device));
    when(scopeResolver.resolve(teacher)).thenReturn(new DeviceScope.InstitutionScope(institution.getId()));
  }
}
