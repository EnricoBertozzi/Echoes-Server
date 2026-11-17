package com.n0hana.echoes_server.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.n0hana.echoes_server.device.exception.DeviceNotFoundException;
import com.n0hana.echoes_server.device.port.DeviceScopeResolver;
import com.n0hana.echoes_server.institution.InstitutionModel;
import com.n0hana.echoes_server.user.model.User;

@ExtendWith(MockitoExtension.class)
public class DeviceServiceTests {

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private DeviceScopeResolver scope;

    @InjectMocks
    private DeviceService deviceService;

    @Test
    @DisplayName("Busca dispositivo pelo MAC normalizado")
    void shouldFindDeviceByMac() {
        String hardwareId = "AA:BB:CC:DD:EE:FF";
        DeviceModel device = DeviceModel.builder()
                .id(UUID.randomUUID())
                .name("Dispositivo")
                .hardwareId(hardwareId)
                .publicKey("pem")
                .build();

        when(deviceRepository.findByHardwareId(hardwareId)).thenReturn(Optional.of(device));

        DeviceModel found = deviceService.findDeviceByHardwareId("  " + hardwareId + "  ");

        assertEquals(device, found);
    }

    @Test
    @DisplayName("Lança exceção quando o MAC não está cadastrado")
    void shouldThrowWhenMacDoesNotExist() {
        when(deviceRepository.findByHardwareId("AA:BB:CC:DD:EE:FF")).thenReturn(Optional.empty());

        assertThrows(DeviceNotFoundException.class,
                () -> deviceService.findDeviceByHardwareId("AA:BB:CC:DD:EE:FF"));
    }

    @Test
    @DisplayName("Lança exceção quando o MAC é vazio")
    void shouldThrowWhenMacIsBlank() {
        assertThrows(DeviceNotFoundException.class, () -> deviceService.findDeviceByHardwareId("   "));
    }

    @Test
    @DisplayName("Busca dispositivo pelo id")
    void shouldFindDeviceById() {
        UUID id = UUID.randomUUID();
        DeviceModel device = DeviceModel.builder().id(id).name("Dispositivo").build();

        when(deviceRepository.findById(id)).thenReturn(Optional.of(device));

        assertEquals(device, deviceService.findDeviceById(id));
    }

    @Test
    @DisplayName("Lança exceção quando o id não existe")
    void shouldThrowWhenIdDoesNotExist() {
        UUID id = UUID.randomUUID();

        when(deviceRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(DeviceNotFoundException.class, () -> deviceService.findDeviceById(id));
    }

    @Test
    @DisplayName("Lista dispositivos paginados por nome")
    void shouldFindAllDevices() {
        int page = 0;
        int size = 2;
        List<DeviceModel> devices = List.of(
                DeviceModel.builder().name("Device A").build(),
                DeviceModel.builder().name("Device B").build());

        Page<DeviceModel> mockPage = new PageImpl<>(
                devices,
                PageRequest.of(page, size),
                devices.size());

        when(deviceRepository.findAll(PageRequest.of(page, size, Sort.by("name"))))
                .thenReturn(mockPage);

        assertEquals(devices, deviceService.findAllDevices(page, size));
    }

    @Test
    @DisplayName("Busca dispositivos pelo nome")
    void shouldFindDevicesByName() {
        int page = 0;
        int size = 10;
        String name = "Device";
        List<DeviceModel> devices = List.of(DeviceModel.builder().name("Device A").build());

        Page<DeviceModel> mockPage = new PageImpl<>(
                devices,
                PageRequest.of(page, size),
                devices.size());

        when(deviceRepository.findByNameContaining(name, PageRequest.of(page, size, Sort.by("name"))))
                .thenReturn(mockPage);

        assertEquals(devices, deviceService.findDevicesByName(name, page, size));
    }

    @Test
    @DisplayName("Gestor busca dispositivo da própria instituição")
    void shouldFindDeviceByIdAsManager() {
        UUID deviceId = UUID.randomUUID();
        UUID institutionId = UUID.randomUUID();

        User user = mock(User.class);

        InstitutionModel institution = mock(InstitutionModel.class);

        when(institution.getId()).thenReturn(institutionId);

        DeviceModel device = DeviceModel.builder()
                .id(deviceId)
                .name("Dispositivo")
                .status(DeviceStatus.ACTIVE)
                .institution(institution)
                .build();

        when(scope.resolve(user))
                .thenReturn(new DeviceScope.InstitutionScope(institutionId));

        when(deviceRepository.findById(deviceId))
                .thenReturn(Optional.of(device));

        assertEquals(
                device,
                deviceService.findDeviceById(deviceId, user));
    }

    @Test
    @DisplayName("Gestor não pode buscar dispositivo de outra instituição")
    void shouldNotFindDeviceFromAnotherInstitution() {
        UUID deviceId = UUID.randomUUID();
        UUID managerInstitutionId = UUID.randomUUID();
        UUID deviceInstitutionId = UUID.randomUUID();

        User user = mock(User.class);

        InstitutionModel institution = mock(InstitutionModel.class);

        when(institution.getId()).thenReturn(deviceInstitutionId);

        DeviceModel device = DeviceModel.builder()
                .id(deviceId)
                .status(DeviceStatus.ACTIVE)
                .institution(institution)
                .build();

        when(scope.resolve(user))
                .thenReturn(
                        new DeviceScope.InstitutionScope(managerInstitutionId));

        when(deviceRepository.findById(deviceId))
                .thenReturn(Optional.of(device));

        assertThrows(
                DeviceNotFoundException.class,
                () -> deviceService.findDeviceById(deviceId, user));
    }
}
