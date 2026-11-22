package com.n0hana.echoes_server.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import com.n0hana.echoes_server.animal.AnimalModel;
import com.n0hana.echoes_server.animal.AnimalRepository;
import com.n0hana.echoes_server.classroom.ClassroomModel;
import com.n0hana.echoes_server.device.DeviceDTO.UpdateDeviceRequest;
import com.n0hana.echoes_server.device.exception.InvalidDeviceUpdateException;
import com.n0hana.echoes_server.institution.InstitutionModel;
import com.n0hana.echoes_server.institution.InstitutionRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeviceLifecycleCoverageTests {

    private static final UUID DEVICE_ID = UUID.randomUUID();

    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private InstitutionRepository institutionRepository;
    @Mock
    private AnimalRepository animalRepository;
    @Mock
    private ApplicationEventPublisher publisher;

    private DeviceLifecycleService service;
    private DeviceModel device;

    @BeforeEach
    void setUp() {
        service = new DeviceLifecycleService(deviceRepository, institutionRepository, animalRepository, publisher);
        device = DeviceModel.builder().id(DEVICE_ID).name("Antigo").hardwareId("hw").publicKey("pem")
                .status(DeviceStatus.ACTIVE).build();
        when(deviceRepository.findById(DEVICE_ID)).thenReturn(Optional.of(device));
        when(deviceRepository.save(any(DeviceModel.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("Atualização com request nulo ou sem campos é recusada")
    void updateWithoutFields() {
        assertThrows(InvalidDeviceUpdateException.class, () -> service.update(DEVICE_ID, null));
        assertThrows(InvalidDeviceUpdateException.class,
                () -> service.update(DEVICE_ID, new UpdateDeviceRequest(null, null, null)));
    }

    @Test
    @DisplayName("Dispositivo sem instituição, ao receber uma, passa a tê-la e perde a turma (nula)")
    void updateInstitutionOnDeviceWithoutOne() {
        InstitutionModel target = InstitutionModel.builder().id(UUID.randomUUID()).active(true).build();
        when(institutionRepository.findById(target.getId())).thenReturn(Optional.of(target));

        DeviceModel result = service.update(DEVICE_ID, new UpdateDeviceRequest(null, target.getId(), null));

        assertSame(target, result.getInstitution());
        assertNull(result.getClassroom());
    }

    @Test
    @DisplayName("Reenviar a mesma instituição mantém a turma do dispositivo")
    void updateSameInstitutionKeepsClassroom() {
        InstitutionModel current = InstitutionModel.builder().id(UUID.randomUUID()).active(true).build();
        ClassroomModel classroom = ClassroomModel.builder().id(UUID.randomUUID()).institution(current).build();
        device.setInstitution(current);
        device.setClassroom(classroom);
        when(institutionRepository.findById(current.getId())).thenReturn(Optional.of(current));

        DeviceModel result = service.update(DEVICE_ID, new UpdateDeviceRequest(null, current.getId(), null));

        assertSame(classroom, result.getClassroom());
    }

    @Test
    @DisplayName("Reassociar o mesmo animal desativado é permitido; outro animal desativado é recusado")
    void updateAnimalRules() {
        AnimalModel same = AnimalModel.builder().id(UUID.randomUUID()).name("A").build();
        same.setActive(false);
        device.setAnimal(same);
        when(animalRepository.findById(same.getId())).thenReturn(Optional.of(same));

        assertSame(same, service.update(DEVICE_ID, new UpdateDeviceRequest(null, null, same.getId())).getAnimal());

        AnimalModel other = AnimalModel.builder().id(UUID.randomUUID()).name("B").build();
        other.setActive(false);
        when(animalRepository.findById(other.getId())).thenReturn(Optional.of(other));
        UUID otherId = other.getId();

        assertThrows(InvalidDeviceUpdateException.class,
                () -> service.update(DEVICE_ID, new UpdateDeviceRequest(null, null, otherId)));
        assertSame(same, device.getAnimal());
    }

    @Test
    @DisplayName("Atualizar só o nome normaliza o espaço e mantém o resto")
    void updateOnlyName() {
        DeviceModel result = service.update(DEVICE_ID, new UpdateDeviceRequest("  Novo  ", null, null));

        assertEquals("Novo", result.getName());
        assertNull(result.getInstitution());
        verify(deviceRepository).save(device);
    }
}
