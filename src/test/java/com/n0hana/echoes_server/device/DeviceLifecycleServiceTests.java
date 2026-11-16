package com.n0hana.echoes_server.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.n0hana.echoes_server.animal.AnimalModel;
import com.n0hana.echoes_server.animal.AnimalRepository;
import com.n0hana.echoes_server.animal.exception.AnimalNotFoundException;
import com.n0hana.echoes_server.device.DeviceDTO.UpdateDeviceRequest;
import com.n0hana.echoes_server.device.exception.DeviceNotFoundException;
import com.n0hana.echoes_server.device.exception.InvalidDeviceStateException;
import com.n0hana.echoes_server.device.exception.InvalidDeviceUpdateException;
import com.n0hana.echoes_server.institution.InstitutionModel;
import com.n0hana.echoes_server.institution.InstitutionRepository;
import com.n0hana.echoes_server.institution.exception.InstitutionNotFoundException;

@ExtendWith(MockitoExtension.class)
class DeviceLifecycleServiceTests {

  private static final UUID DEVICE_ID = UUID.randomUUID();
  private static final UUID INSTITUTION_ID = UUID.randomUUID();
  private static final UUID ANIMAL_ID = UUID.randomUUID();
  private static final UUID ADMIN_ID = UUID.randomUUID();

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

  @BeforeEach
  void setUp() {
    service = new DeviceLifecycleService(deviceRepository, institutionRepository, animalRepository, eventPublisher);
    institution = InstitutionModel.builder().id(INSTITUTION_ID).active(true).build();
  }

  private DeviceModel device(DeviceStatus status, InstitutionModel institution) {
    return DeviceModel.builder()
        .id(DEVICE_ID)
        .hardwareId("AA:BB:CC:DD:EE:FF")
        .publicKey("pem")
        .status(status)
        .institution(institution)
        .build();
  }

  private void stubFind(DeviceModel device) {
    when(deviceRepository.findById(DEVICE_ID)).thenReturn(Optional.of(device));
  }

  private void stubSave() {
    when(deviceRepository.save(any(DeviceModel.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  @DisplayName("Aprova dispositivo pendente: associa a instituição, ativa e registra quem aprovou")
  void shouldApprovePendingDevice() {
    stubFind(device(DeviceStatus.PENDING, null));
    stubSave();
    when(institutionRepository.findById(INSTITUTION_ID)).thenReturn(Optional.of(institution));

    DeviceModel result = service.approve(DEVICE_ID, INSTITUTION_ID, ADMIN_ID);

    assertEquals(DeviceStatus.ACTIVE, result.getStatus());
    assertEquals(institution, result.getInstitution());
    assertEquals(ADMIN_ID, result.getApprovedBy());
    assertNotNull(result.getApprovedAt());

    ArgumentCaptor<DeviceStatusChanged> captor = ArgumentCaptor.forClass(DeviceStatusChanged.class);
    verify(eventPublisher).publishEvent(captor.capture());
    assertEquals(DEVICE_ID, captor.getValue().deviceId());
    assertEquals(DeviceStatus.ACTIVE, captor.getValue().status());
  }

  @Test
  @DisplayName("Não aprova dispositivo que não está pendente")
  void shouldNotApproveNonPendingDevice() {
    stubFind(device(DeviceStatus.ACTIVE, institution));

    assertThrows(InvalidDeviceStateException.class,
        () -> service.approve(DEVICE_ID, INSTITUTION_ID, ADMIN_ID));

    verify(deviceRepository, never()).save(any(DeviceModel.class));
  }

  @Test
  @DisplayName("Não aprova com instituição inexistente")
  void shouldNotApproveWithUnknownInstitution() {
    stubFind(device(DeviceStatus.PENDING, null));
    when(institutionRepository.findById(INSTITUTION_ID)).thenReturn(Optional.empty());

    assertThrows(InstitutionNotFoundException.class,
        () -> service.approve(DEVICE_ID, INSTITUTION_ID, ADMIN_ID));

    verify(deviceRepository, never()).save(any(DeviceModel.class));
  }

  @Test
  @DisplayName("Falha quando o dispositivo não existe")
  void shouldFailWhenDeviceNotFound() {
    when(deviceRepository.findById(DEVICE_ID)).thenReturn(Optional.empty());

    assertThrows(DeviceNotFoundException.class, () -> service.deactivate(DEVICE_ID));
  }

  @Test
  @DisplayName("Inativa dispositivo ativo e avisa a mudança")
  void shouldDeactivateActiveDevice() {
    stubFind(device(DeviceStatus.ACTIVE, institution));
    stubSave();

    DeviceModel result = service.deactivate(DEVICE_ID);

    assertEquals(DeviceStatus.INACTIVE, result.getStatus());
    verify(eventPublisher).publishEvent(any(DeviceStatusChanged.class));
  }

  @Test
  @DisplayName("Só inativa dispositivo ativo")
  void shouldNotDeactivateNonActiveDevice() {
    stubFind(device(DeviceStatus.PENDING, null));

    assertThrows(InvalidDeviceStateException.class, () -> service.deactivate(DEVICE_ID));
  }

  @Test
  @DisplayName("Ativa dispositivo inativo")
  void shouldActivateInactiveDevice() {
    stubFind(device(DeviceStatus.INACTIVE, institution));
    stubSave();

    DeviceModel result = service.activate(DEVICE_ID);

    assertEquals(DeviceStatus.ACTIVE, result.getStatus());
  }

  @Test
  @DisplayName("Dispositivo pendente não é ativado: precisa ser aprovado com instituição")
  void shouldNotActivatePendingDevice() {
    stubFind(device(DeviceStatus.PENDING, null));

    assertThrows(InvalidDeviceStateException.class, () -> service.activate(DEVICE_ID));
  }

  @Test
  @DisplayName("Move dispositivo aprovado para outra instituição, mantendo o status")
  void shouldChangeInstitution() {
    stubFind(device(DeviceStatus.ACTIVE, InstitutionModel.builder().id(UUID.randomUUID()).build()));
    stubSave();
    when(institutionRepository.findById(INSTITUTION_ID)).thenReturn(Optional.of(institution));

    DeviceModel result = service.changeInstitution(DEVICE_ID, INSTITUTION_ID);

    assertEquals(institution, result.getInstitution());
    assertEquals(DeviceStatus.ACTIVE, result.getStatus());
  }

  @Test
  @DisplayName("Dispositivo pendente não pode ser movido")
  void shouldNotChangeInstitutionOfPendingDevice() {
    stubFind(device(DeviceStatus.PENDING, null));

    assertThrows(InvalidDeviceStateException.class,
        () -> service.changeInstitution(DEVICE_ID, INSTITUTION_ID));
  }

  @Test
  @DisplayName("Atualiza apenas o nome, preservando instituição e animal")
  void shouldUpdateNameKeepingInstitutionAndAnimal() {
    AnimalModel animal = AnimalModel.builder().id(ANIMAL_ID).name("Boi").build();
    DeviceModel device = device(DeviceStatus.ACTIVE, institution);
    device.setAnimal(animal);
    stubFind(device);
    stubSave();

    DeviceModel result = service.update(DEVICE_ID, new UpdateDeviceRequest("  Sensor Norte  ", null, null));

    assertEquals("Sensor Norte", result.getName());
    assertEquals(institution, result.getInstitution());
    assertEquals(animal, result.getAnimal());
  }

  @Test
  @DisplayName("Atualiza instituição e animal no mesmo PATCH")
  void shouldUpdateInstitutionAndAnimal() {
    InstitutionModel newInstitution = InstitutionModel.builder().id(UUID.randomUUID()).build();
    AnimalModel animal = AnimalModel.builder().id(ANIMAL_ID).name("Égua").build();
    stubFind(device(DeviceStatus.ACTIVE, institution));
    stubSave();
    when(institutionRepository.findById(newInstitution.getId())).thenReturn(Optional.of(newInstitution));
    when(animalRepository.findById(ANIMAL_ID)).thenReturn(Optional.of(animal));

    DeviceModel result = service.update(DEVICE_ID,
        new UpdateDeviceRequest(null, newInstitution.getId(), ANIMAL_ID));

    assertEquals(newInstitution, result.getInstitution());
    assertEquals(animal, result.getAnimal());
    assertEquals(DeviceStatus.ACTIVE, result.getStatus());
  }

  @Test
  @DisplayName("Troca de instituição de dispositivo pendente continua bloqueada")
  void shouldNotUpdateInstitutionOfPendingDevice() {
    stubFind(device(DeviceStatus.PENDING, null));

    assertThrows(InvalidDeviceStateException.class,
        () -> service.update(DEVICE_ID, new UpdateDeviceRequest(null, INSTITUTION_ID, null)));

    verify(deviceRepository, never()).save(any(DeviceModel.class));
  }

  @Test
  @DisplayName("Omitir animalId mantém o animal atual: o vínculo não pode ser removido")
  void shouldNotRemoveAnimalWhenAnimalIdIsOmitted() {
    AnimalModel animal = AnimalModel.builder().id(ANIMAL_ID).name("Boi").build();
    DeviceModel device = device(DeviceStatus.ACTIVE, institution);
    device.setAnimal(animal);
    stubFind(device);
    stubSave();

    DeviceModel result = service.update(DEVICE_ID, new UpdateDeviceRequest("Sensor Norte", null, null));

    assertEquals(animal, result.getAnimal());
  }

  @Test
  @DisplayName("Não aceita PATCH sem nenhum campo")
  void shouldRejectEmptyUpdate() {
    assertThrows(InvalidDeviceUpdateException.class,
        () -> service.update(DEVICE_ID, new UpdateDeviceRequest(null, null, null)));
  }

  @Test
  @DisplayName("Não aceita nome em branco")
  void shouldRejectBlankName() {
    assertThrows(InvalidDeviceUpdateException.class,
        () -> service.update(DEVICE_ID, new UpdateDeviceRequest("   ", null, null)));
  }

  @Test
  @DisplayName("Não aceita nome acima de 255 caracteres")
  void shouldRejectTooLongName() {
    assertThrows(InvalidDeviceUpdateException.class,
        () -> service.update(DEVICE_ID, new UpdateDeviceRequest("x".repeat(256), null, null)));
  }

  @Test
  @DisplayName("Falha ao vincular animal inexistente")
  void shouldRejectUnknownAnimal() {
    stubFind(device(DeviceStatus.ACTIVE, institution));
    when(animalRepository.findById(ANIMAL_ID)).thenReturn(Optional.empty());

    assertThrows(AnimalNotFoundException.class,
        () -> service.update(DEVICE_ID, new UpdateDeviceRequest(null, null, ANIMAL_ID)));

    verify(deviceRepository, never()).save(any(DeviceModel.class));
  }

  @Test
  @DisplayName("Falha ao atualizar dispositivo inexistente")
  void shouldFailUpdateWhenDeviceNotFound() {
    when(deviceRepository.findById(DEVICE_ID)).thenReturn(Optional.empty());

    assertThrows(DeviceNotFoundException.class,
        () -> service.update(DEVICE_ID, new UpdateDeviceRequest("Novo nome", null, null)));
  }

  @Test
  @DisplayName("Exclui o dispositivo em qualquer estado")
  void shouldDeleteDevice() {
    DeviceModel device = device(DeviceStatus.PENDING, null);
    stubFind(device);

    service.delete(DEVICE_ID);

    verify(deviceRepository).delete(device);
  }
}
