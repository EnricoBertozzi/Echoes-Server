package com.n0hana.echoes_server.device;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.n0hana.echoes_server.animal.AnimalRepository;
import com.n0hana.echoes_server.classroom.ClassroomModel;
import com.n0hana.echoes_server.device.DeviceDTO.UpdateDeviceRequest;
import com.n0hana.echoes_server.institution.InstitutionModel;
import com.n0hana.echoes_server.institution.InstitutionRepository;

@ExtendWith(MockitoExtension.class)
class DeviceLifecycleClassroomTests {

  private static final UUID DEVICE_ID = UUID.randomUUID();

  @Mock
  private DeviceRepository deviceRepository;

  @Mock
  private InstitutionRepository institutionRepository;

  @Mock
  private AnimalRepository animalRepository;

  @Mock
  private ApplicationEventPublisher eventPublisher;

  private DeviceLifecycleService service;
  private InstitutionModel institution;
  private ClassroomModel classroom;

  @BeforeEach
  void setUp() {
    service = new DeviceLifecycleService(deviceRepository, institutionRepository, animalRepository, eventPublisher);
    institution = InstitutionModel.builder().id(UUID.randomUUID()).active(true).build();
    classroom = ClassroomModel.builder().id(UUID.randomUUID()).institution(institution).build();
  }

  private DeviceModel deviceInClassroom() {
    DeviceModel device = DeviceModel.builder()
        .id(DEVICE_ID)
        .hardwareId("AA:BB:CC:DD:EE:FF")
        .publicKey("pem")
        .status(DeviceStatus.ACTIVE)
        .institution(institution)
        .build();
    device.setClassroom(classroom);
    return device;
  }

  private void stubFindAndSave(DeviceModel device) {
    when(deviceRepository.findById(DEVICE_ID)).thenReturn(Optional.of(device));
    when(deviceRepository.save(any(DeviceModel.class))).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  @DisplayName("Trocar de instituição tira o dispositivo da turma")
  void changingInstitutionRemovesDeviceFromClassroom() {
    InstitutionModel newInstitution = InstitutionModel.builder().id(UUID.randomUUID()).active(true).build();
    stubFindAndSave(deviceInClassroom());
    when(institutionRepository.findById(newInstitution.getId())).thenReturn(Optional.of(newInstitution));

    DeviceModel result = service.update(DEVICE_ID, new UpdateDeviceRequest(null, newInstitution.getId(), null));

    assertSame(newInstitution, result.getInstitution());
    assertNull(result.getClassroom());
  }

  @Test
  @DisplayName("Informar a mesma instituição mantém o dispositivo na turma")
  void sameInstitutionKeepsClassroom() {
    stubFindAndSave(deviceInClassroom());
    when(institutionRepository.findById(institution.getId())).thenReturn(Optional.of(institution));

    DeviceModel result = service.update(DEVICE_ID, new UpdateDeviceRequest(null, institution.getId(), null));

    assertSame(classroom, result.getClassroom());
  }

  @Test
  @DisplayName("Alterar só o nome mantém o dispositivo na turma")
  void renamingKeepsClassroom() {
    stubFindAndSave(deviceInClassroom());

    DeviceModel result = service.update(DEVICE_ID, new UpdateDeviceRequest("Sensor Norte", null, null));

    assertSame(classroom, result.getClassroom());
  }
}
