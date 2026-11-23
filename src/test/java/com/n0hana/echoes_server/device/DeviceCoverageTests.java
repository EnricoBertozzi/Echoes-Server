package com.n0hana.echoes_server.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

import com.n0hana.echoes_server.animal.AnimalModel;
import com.n0hana.echoes_server.classroom.ClassroomModel;
import com.n0hana.echoes_server.device.DeviceDTO.DeviceInfo;
import com.n0hana.echoes_server.device.DeviceDTO.DeviceInstitutionRequest;
import com.n0hana.echoes_server.device.DeviceDTO.UpdateDeviceRequest;
import com.n0hana.echoes_server.device.exception.DeviceNotFoundException;
import com.n0hana.echoes_server.device.port.DeviceScopeResolver;
import com.n0hana.echoes_server.infra.redis.CodeType;
import com.n0hana.echoes_server.infra.redis.RedisRepository;
import com.n0hana.echoes_server.infra.redis.RedisRepositoryFactory;
import com.n0hana.echoes_server.institution.InstitutionModel;
import com.n0hana.echoes_server.user.model.Admin;
import com.n0hana.echoes_server.user.model.Manager;
import com.n0hana.echoes_server.user.model.User;

@ExtendWith(MockitoExtension.class)
class DeviceCoverageTests {

    private static final UUID ID = UUID.randomUUID();
    private static final UUID INST = UUID.randomUUID();

    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private DeviceScopeResolver scopeResolver;
    @Mock
    private DeviceLifecycleService lifecycleService;
    @Mock
    private DeviceService mockedService;

    private DeviceService service;
    private DeviceController controller;

    @BeforeEach
    void setUp() {
        service = new DeviceService(deviceRepository, scopeResolver);
        controller = new DeviceController(mockedService, lifecycleService);
    }

    private DeviceModel device(DeviceStatus status, InstitutionModel institution) {
        return DeviceModel.builder().id(ID).name("Disp").hardwareId("AA:BB").publicKey("pem")
                .status(status).institution(institution).build();
    }

    private InstitutionModel institution(UUID id) {
        return InstitutionModel.builder().id(id).name("Inst").active(true).build();
    }

    // ---- DeviceService ----

    @Test
    @DisplayName("MAC nulo é tratado como dispositivo não encontrado")
    void nullHardwareId() {
        assertThrows(DeviceNotFoundException.class, () -> service.findDeviceByHardwareId(null));
    }

    @Test
    @DisplayName("Admin vê qualquer dispositivo por id, mesmo pendente")
    void adminFindsAnyDevice() {
        User admin = new Admin();
        DeviceModel pending = device(DeviceStatus.PENDING, null);
        when(scopeResolver.resolve(admin)).thenReturn(new DeviceScope.All());
        when(deviceRepository.findById(ID)).thenReturn(Optional.of(pending));

        assertSame(pending, service.findDeviceById(ID, admin));
    }

    @Test
    @DisplayName("Gestor vê dispositivo ativo da própria instituição")
    void managerFindsOwnDevice() {
        User manager = new Manager();
        DeviceModel own = device(DeviceStatus.ACTIVE, institution(INST));
        when(scopeResolver.resolve(manager)).thenReturn(new DeviceScope.InstitutionScope(INST));
        when(deviceRepository.findById(ID)).thenReturn(Optional.of(own));

        assertSame(own, service.findDeviceById(ID, manager));
    }

    @Test
    @DisplayName("Gestor não vê dispositivo pendente, sem instituição ou de outra instituição")
    void managerCannotSeeForeignDevices() {
        User manager = new Manager();
        when(scopeResolver.resolve(manager)).thenReturn(new DeviceScope.InstitutionScope(INST));

        when(deviceRepository.findById(ID)).thenReturn(Optional.of(device(DeviceStatus.PENDING, institution(INST))));
        assertThrows(DeviceNotFoundException.class, () -> service.findDeviceById(ID, manager));

        when(deviceRepository.findById(ID)).thenReturn(Optional.of(device(DeviceStatus.ACTIVE, null)));
        assertThrows(DeviceNotFoundException.class, () -> service.findDeviceById(ID, manager));

        when(deviceRepository.findById(ID))
                .thenReturn(Optional.of(device(DeviceStatus.ACTIVE, institution(UUID.randomUUID()))));
        assertThrows(DeviceNotFoundException.class, () -> service.findDeviceById(ID, manager));
    }

    @Test
    @DisplayName("Busca por id inexistente lança DeviceNotFoundException")
    void findByIdMissing() {
        when(deviceRepository.findById(ID)).thenReturn(Optional.empty());

        assertThrows(DeviceNotFoundException.class, () -> service.findDeviceById(ID));
    }

    @Test
    @DisplayName("Listagens paginadas ordenam por nome e devolvem o conteúdo da página")
    void listings() {
        DeviceModel d = device(DeviceStatus.ACTIVE, null);
        PageRequest expected = PageRequest.of(1, 5, org.springframework.data.domain.Sort.by("name"));
        when(deviceRepository.findAll(expected)).thenReturn(new PageImpl<>(List.of(d)));
        when(deviceRepository.findByStatus(DeviceStatus.ACTIVE, expected)).thenReturn(new PageImpl<>(List.of(d)));
        when(deviceRepository.findByNameContaining("Di", expected)).thenReturn(new PageImpl<>(List.of(d)));

        assertEquals(List.of(d), service.findAllDevices(1, 5));
        assertEquals(List.of(d), service.findDevicesByStatus(DeviceStatus.ACTIVE, 1, 5));
        assertEquals(List.of(d), service.findDevicesByName("Di", 1, 5));
    }

    @Test
    @DisplayName("Admin lista incluindo pendentes e nome em branco vira filtro nulo")
    void adminFindDevicesIncludesPending() {
        User admin = new Admin();
        Pageable pageable = PageRequest.of(0, 10);
        Page<DeviceModel> page = new PageImpl<>(List.of());
        when(scopeResolver.resolve(admin)).thenReturn(new DeviceScope.All());
        when(deviceRepository.findDevices(null, DeviceStatus.ACTIVE, INST, true, DeviceStatus.PENDING, pageable))
                .thenReturn(page);

        assertSame(page, service.findDevices(admin, "  ", DeviceStatus.ACTIVE, INST, pageable));
    }

    @Test
    @DisplayName("Gestor lista só da própria instituição, sem pendentes, e mantém o nome informado")
    void managerFindDevicesExcludesPending() {
        User manager = new Manager();
        Pageable pageable = PageRequest.of(0, 10);
        Page<DeviceModel> page = new PageImpl<>(List.of());
        when(scopeResolver.resolve(manager)).thenReturn(new DeviceScope.InstitutionScope(INST));
        when(deviceRepository.findDevices("abc", null, INST, false, DeviceStatus.PENDING, pageable))
                .thenReturn(page);

        assertSame(page, service.findDevices(manager, "abc", null, null, pageable));
        assertThrows(AccessDeniedException.class,
                () -> service.findDevices(manager, null, null, UUID.randomUUID(), pageable));
    }

    // ---- DeviceDTO ----

    @Test
    @DisplayName("DeviceInfo.from copia todos os campos e relacionamentos")
    void deviceInfoFull() {
        AnimalModel animal = AnimalModel.builder().id(UUID.randomUUID()).name("Rex").build();
        ClassroomModel classroom = mock(ClassroomModel.class);
        UUID classroomId = UUID.randomUUID();
        when(classroom.getId()).thenReturn(classroomId);
        DeviceModel d = device(DeviceStatus.ACTIVE, institution(INST));
        d.setAnimal(animal);
        d.setClassroom(classroom);
        LocalDateTime now = LocalDateTime.now();
        d.setCreatedAt(now);
        d.setLastSeenAt(now.plusMinutes(1));

        DeviceInfo info = DeviceInfo.from(d);

        assertEquals(ID, info.id());
        assertEquals("Disp", info.name());
        assertEquals("AA:BB", info.hardwareId());
        assertEquals(DeviceStatus.ACTIVE, info.status());
        assertEquals(INST, info.institutionId());
        assertEquals("Inst", info.institutionName());
        assertEquals(animal.getId(), info.animalId());
        assertEquals("Rex", info.animalName());
        assertEquals(classroomId, info.classroomId());
        assertEquals(now, info.createdAt());
        assertEquals(now.plusMinutes(1), info.lastSeenAt());
    }

    @Test
    @DisplayName("DeviceInfo.from aceita dispositivo sem instituição, animal e turma")
    void deviceInfoEmptyRelations() {
        DeviceInfo info = DeviceInfo.from(device(DeviceStatus.PENDING, null));

        assertNull(info.institutionId());
        assertNull(info.institutionName());
        assertNull(info.animalId());
        assertNull(info.animalName());
        assertNull(info.classroomId());
    }

    @Test
    @DisplayName("Requests do DeviceDTO expõem seus componentes")
    void requestRecords() {
        assertEquals(INST, new DeviceInstitutionRequest(INST).institutionId());
        UpdateDeviceRequest update = new UpdateDeviceRequest("n", INST, ID);
        assertEquals("n", update.name());
        assertEquals(INST, update.institutionId());
        assertEquals(ID, update.animalId());
        assertEquals(new DeviceInstitutionRequest(INST), new DeviceInstitutionRequest(INST));
    }

    // ---- DeviceController ----

    @Test
    @DisplayName("GET /me sem dispositivo autenticado lança AccessDeniedException")
    void meWithoutDevice() {
        assertThrows(AccessDeniedException.class, () -> controller.findMyDevice(null));
    }

    @Test
    @DisplayName("GET /me devolve os dados do dispositivo autenticado")
    void meWithDevice() {
        ResponseEntity<DeviceInfo> response = controller.findMyDevice(device(DeviceStatus.ACTIVE, null));

        assertEquals(200, response.getStatusCode().value());
        assertEquals("Disp", response.getBody().name());
    }

    @Test
    @DisplayName("GET /devices converte a página do service em DeviceInfo")
    void controllerFindAll() {
        User user = new Admin();
        Pageable pageable = PageRequest.of(0, 5);
        when(mockedService.findDevices(user, "n", DeviceStatus.ACTIVE, INST, pageable))
                .thenReturn(new PageImpl<>(List.of(device(DeviceStatus.ACTIVE, null))));

        ResponseEntity<Page<DeviceInfo>> response = controller.findAllDevices(user, "n", DeviceStatus.ACTIVE, INST,
                pageable);

        assertEquals(1, response.getBody().getTotalElements());
        assertEquals("Disp", response.getBody().getContent().get(0).name());
    }

    @Test
    @DisplayName("GET /devices/search e /devices/{id} delegam ao service")
    void controllerSearchAndFindById() {
        User user = new Admin();
        DeviceModel d = device(DeviceStatus.ACTIVE, null);
        when(mockedService.findDevicesByName("x", 0, 10)).thenReturn(List.of(d));
        when(mockedService.findDeviceById(ID, user)).thenReturn(d);

        assertEquals(1, controller.findDevicesByName("x", 0, 10).getBody().size());
        assertEquals(ID, controller.findDeviceById(ID, user).getBody().id());
    }

    @Test
    @DisplayName("Ações de ciclo de vida delegam ao DeviceLifecycleService")
    void controllerLifecycle() {
        DeviceModel d = device(DeviceStatus.ACTIVE, null);
        User admin = mock(User.class);
        UUID adminId = UUID.randomUUID();
        when(admin.getId()).thenReturn(adminId);
        UpdateDeviceRequest update = new UpdateDeviceRequest("novo", null, null);
        when(lifecycleService.approve(ID, INST, adminId)).thenReturn(d);
        when(lifecycleService.update(ID, update)).thenReturn(d);
        when(lifecycleService.changeInstitution(ID, INST)).thenReturn(d);
        when(lifecycleService.activate(ID)).thenReturn(d);
        when(lifecycleService.deactivate(ID)).thenReturn(d);

        assertEquals(ID, controller.approve(ID, new DeviceInstitutionRequest(INST), admin).getBody().id());
        assertEquals(ID, controller.update(ID, update).getBody().id());
        assertEquals(ID, controller.changeInstitution(ID, new DeviceInstitutionRequest(INST)).getBody().id());
        assertEquals(ID, controller.activate(ID).getBody().id());
        assertEquals(ID, controller.deactivate(ID).getBody().id());
        ResponseEntity<Void> deleted = controller.delete(ID);

        assertEquals(204, deleted.getStatusCode().value());
        verify(lifecycleService).delete(ID);
    }

    // ---- DeviceTokenRevocationRepository ----

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("Revogação grava o instante em segundos e lê o corte de volta como Long")
    void revocationRepository() {
        RedisRepositoryFactory factory = mock(RedisRepositoryFactory.class);
        RedisRepository<String> store = mock(RedisRepository.class);
        when(factory.string(CodeType.DEVICE_TOKENS_REVOKED)).thenReturn(store);
        when(store.find(ID.toString())).thenReturn(Optional.of("1700000000"));
        when(store.find(INST.toString())).thenReturn(Optional.empty());
        DeviceTokenRevocationRepository repository = new DeviceTokenRevocationRepository(factory);

        repository.revokeUntil(ID, 1700000000L);

        verify(store).save(ID.toString(), "1700000000");
        assertEquals(Optional.of(1700000000L), repository.findCutoff(ID));
        assertEquals(Optional.empty(), repository.findCutoff(INST));
    }
}
