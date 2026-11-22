package com.n0hana.echoes_server.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.n0hana.echoes_server.animal.AnimalModel;
import com.n0hana.echoes_server.auscultation.AuscultationPointModel;
import com.n0hana.echoes_server.auscultation.AuscultationPointRepository;
import com.n0hana.echoes_server.classroom.ClassroomMemberRepository;
import com.n0hana.echoes_server.device.DeviceScenarioDTO.CreateClinicalPresetRequest;
import com.n0hana.echoes_server.device.DeviceScenarioDTO.PresetPoint;
import com.n0hana.echoes_server.device.exception.InvalidDeviceUpdateException;
import com.n0hana.echoes_server.device.port.DeviceScopeResolver;
import com.n0hana.echoes_server.institution.InstitutionModel;
import com.n0hana.echoes_server.scenario.ScenarioModel;
import com.n0hana.echoes_server.scenario.ScenarioRepository;
import com.n0hana.echoes_server.user.model.Admin;

@ExtendWith(MockitoExtension.class)
class ClinicalScenarioPresetServiceTests {

  @Mock DeviceRepository devices;
  @Mock AuscultationPointRepository points;
  @Mock ScenarioRepository scenarios;
  @Mock DeviceScenarioRepository selections;
  @Mock ClinicalScenarioPresetRepository presets;
  @Mock DeviceScopeResolver scopes;
  @Mock ClassroomMemberRepository members;
  @InjectMocks DeviceScenarioService service;

  private DeviceModel device;
  private Admin user;
  private AuscultationPointModel cardiac, pulmonary;
  private ScenarioModel normal, crepitation;

  @BeforeEach
  void setup() {
    InstitutionModel institution = InstitutionModel.builder().id(UUID.randomUUID()).build();
    AnimalModel animal = AnimalModel.builder().id(UUID.randomUUID()).name("Animal").build();
    device = DeviceModel.builder().id(UUID.randomUUID()).status(DeviceStatus.ACTIVE)
        .animal(animal).institution(institution).build();
    cardiac = AuscultationPointModel.builder().id(UUID.randomUUID()).position("01 - Cardiaco")
        .animal(animal).build();
    pulmonary = AuscultationPointModel.builder().id(UUID.randomUUID()).position("02 - Pulmonar")
        .animal(animal).build();
    normal = ScenarioModel.builder().id(UUID.randomUUID()).name("Normal")
        .auscultationPoint(cardiac).build();
    crepitation = ScenarioModel.builder().id(UUID.randomUUID()).name("Crepitacao")
        .auscultationPoint(pulmonary).build();
    user = new Admin();
    user.setId(UUID.randomUUID());
  }

  private void allow() {
    when(devices.findById(device.getId())).thenReturn(Optional.of(device));
    when(scopes.resolve(user)).thenReturn(new DeviceScope.All());
  }

  private Map<UUID, UUID> assignments() {
    Map<UUID, UUID> selected = new LinkedHashMap<>();
    selected.put(cardiac.getId(), normal.getId());
    selected.put(pulmonary.getId(), crepitation.getId());
    return selected;
  }

  private void mockCompleteAnimal() {
    when(points.findByAnimalIdOrderByPositionAsc(device.getAnimal().getId()))
        .thenReturn(List.of(cardiac, pulmonary));
    when(scenarios.findAllById(any())).thenReturn(List.of(normal, crepitation));
  }

  @Test
  void createsPresetWithoutChangingCurrentDeviceConfiguration() {
    allow();
    mockCompleteAnimal();
    when(presets.save(any())).thenAnswer(invocation -> {
      ClinicalScenarioPresetModel preset = invocation.getArgument(0);
      preset.setId(UUID.randomUUID());
      return preset;
    });

    var request = new CreateClinicalPresetRequest("Caso Patologico", "Teste",
        List.of(new PresetPoint(cardiac.getId(), normal.getId()),
            new PresetPoint(pulmonary.getId(), crepitation.getId())));

    var response = service.createPreset(user, device.getId(), request);
    assertEquals(2, response.points().size());
    verify(selections, never()).saveAll(any());
  }

  @Test
  void activatesAllPointsInOneSaveAll() {
    allow();
    mockCompleteAnimal();
    ClinicalScenarioPresetModel preset = new ClinicalScenarioPresetModel();
    preset.setSelections(assignments());
    when(presets.findByIdAndAnimalId(any(), any())).thenReturn(Optional.of(preset));
    when(selections.findByDeviceId(device.getId())).thenReturn(List.of());

    service.activatePreset(user, device.getId(), UUID.randomUUID());

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<DeviceScenarioModel>> saved = ArgumentCaptor.forClass(List.class);
    verify(selections).saveAll(saved.capture());
    assertEquals(2, saved.getValue().size());
    assertEquals(normal.getId(), saved.getValue().get(0).getScenario().getId());
    assertEquals(crepitation.getId(), saved.getValue().get(1).getScenario().getId());
  }

  @Test
  void rejectsIncompletePresetWithoutWriting() {
    allow();
    when(points.findByAnimalIdOrderByPositionAsc(device.getAnimal().getId()))
        .thenReturn(List.of(cardiac, pulmonary));
    ClinicalScenarioPresetModel preset = new ClinicalScenarioPresetModel();
    preset.setSelections(Map.of(cardiac.getId(), normal.getId()));
    when(presets.findByIdAndAnimalId(any(), any())).thenReturn(Optional.of(preset));

    assertThrows(InvalidDeviceUpdateException.class,
        () -> service.activatePreset(user, device.getId(), UUID.randomUUID()));
    verify(selections, never()).saveAll(any());
  }

  @Test
  void rejectsAudioAssignedToWrongPoint() {
    allow();
    mockCompleteAnimal();
    Map<UUID, UUID> wrong = new LinkedHashMap<>();
    wrong.put(cardiac.getId(), crepitation.getId());
    wrong.put(pulmonary.getId(), normal.getId());
    ClinicalScenarioPresetModel preset = new ClinicalScenarioPresetModel();
    preset.setSelections(wrong);
    when(presets.findByIdAndAnimalId(any(), any())).thenReturn(Optional.of(preset));

    assertThrows(InvalidDeviceUpdateException.class,
        () -> service.activatePreset(user, device.getId(), UUID.randomUUID()));
    verify(selections, never()).saveAll(any());
  }

  private void otherInstitutionTeacher() {
    when(devices.findById(device.getId())).thenReturn(Optional.of(device));
    when(scopes.resolve(user)).thenReturn(new DeviceScope.InstitutionScope(UUID.randomUUID()));
  }

  @Test
  void teacherOfAnotherInstitutionCannotCreatePreset() {
    otherInstitutionTeacher();
    var request = new CreateClinicalPresetRequest("Caso", "x",
        List.of(new PresetPoint(cardiac.getId(), normal.getId())));

    assertThrows(org.springframework.security.access.AccessDeniedException.class,
        () -> service.createPreset(user, device.getId(), request));
    verify(presets, never()).save(any());
  }

  @Test
  void teacherOfAnotherInstitutionCannotListPresets() {
    otherInstitutionTeacher();

    assertThrows(org.springframework.security.access.AccessDeniedException.class,
        () -> service.listPresets(user, device.getId()));
    verify(presets, never()).findByAnimalIdOrderByNameAsc(any());
  }

  @Test
  void teacherOfAnotherInstitutionCannotActivatePreset() {
    otherInstitutionTeacher();

    assertThrows(org.springframework.security.access.AccessDeniedException.class,
        () -> service.activatePreset(user, device.getId(), UUID.randomUUID()));
    verify(selections, never()).saveAll(any());
  }
}
