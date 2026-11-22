package com.n0hana.echoes_server.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import com.n0hana.echoes_server.animal.AnimalModel;
import com.n0hana.echoes_server.auscultation.AuscultationPointModel;
import com.n0hana.echoes_server.auscultation.AuscultationPointRepository;
import com.n0hana.echoes_server.auscultation.exception.AuscultationPointNotFound;
import com.n0hana.echoes_server.classroom.ClassroomModel;
import com.n0hana.echoes_server.device.DeviceScenarioDTO.ClinicalPresetInfo;
import com.n0hana.echoes_server.device.DeviceScenarioDTO.CreateClinicalPresetRequest;
import com.n0hana.echoes_server.device.DeviceScenarioDTO.DeviceConfig;
import com.n0hana.echoes_server.device.DeviceScenarioDTO.PointConfig;
import com.n0hana.echoes_server.device.DeviceScenarioDTO.PresetPoint;
import com.n0hana.echoes_server.device.DeviceScenarioDTO.ScenarioRef;
import com.n0hana.echoes_server.device.DeviceScenarioDTO.SelectScenarioRequest;
import com.n0hana.echoes_server.device.exception.InvalidDeviceUpdateException;
import com.n0hana.echoes_server.device.port.DeviceScopeResolver;
import com.n0hana.echoes_server.institution.InstitutionModel;
import com.n0hana.echoes_server.scenario.ScenarioModel;
import com.n0hana.echoes_server.scenario.ScenarioRepository;
import com.n0hana.echoes_server.scenario.exception.ScenarioNotFoundException;
import com.n0hana.echoes_server.user.model.Admin;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeviceScenarioCoverageTests {

    @Mock
    private DeviceRepository devices;
    @Mock
    private AuscultationPointRepository points;
    @Mock
    private ScenarioRepository scenarios;
    @Mock
    private DeviceScenarioRepository selections;
    @Mock
    private ClinicalScenarioPresetRepository presets;
    @Mock
    private DeviceScopeResolver scopes;

    private DeviceScenarioService service;
    private Admin admin;
    private InstitutionModel institution;
    private AnimalModel animal;
    private DeviceModel device;
    private AuscultationPointModel p1, p2, p3;
    private ScenarioModel s1, s2, s3;

    @BeforeEach
    void setUp() {
        service = new DeviceScenarioService(devices, points, scenarios, selections, presets, scopes);
        admin = new Admin();
        institution = InstitutionModel.builder().id(UUID.randomUUID()).name("I").active(true).build();
        animal = AnimalModel.builder().id(UUID.randomUUID()).name("A").build();
        device = DeviceModel.builder().id(UUID.randomUUID()).status(DeviceStatus.ACTIVE)
                .animal(animal).institution(institution).build();
        p1 = AuscultationPointModel.builder().id(UUID.randomUUID()).position("01").animal(animal).build();
        p2 = AuscultationPointModel.builder().id(UUID.randomUUID()).position("02").animal(animal).build();
        p3 = AuscultationPointModel.builder().id(UUID.randomUUID()).position("03").animal(animal).build();
        s1 = ScenarioModel.builder().id(UUID.randomUUID()).name("S1").auscultationPoint(p1).build();
        s2 = ScenarioModel.builder().id(UUID.randomUUID()).name("S2").auscultationPoint(p2).build();
        s3 = ScenarioModel.builder().id(UUID.randomUUID()).name("S3").auscultationPoint(p3).build();
        when(devices.findById(device.getId())).thenReturn(Optional.of(device));
        when(scopes.resolve(admin)).thenReturn(new DeviceScope.All());
        when(points.findByAnimalIdOrderByPositionAsc(animal.getId())).thenReturn(List.of(p1, p2));
        when(scenarios.findAllById(any())).thenReturn(List.of(s1, s2));
    }

    private Map<UUID, UUID> full() {
        Map<UUID, UUID> map = new LinkedHashMap<>();
        map.put(p1.getId(), s1.getId());
        map.put(p2.getId(), s2.getId());
        return map;
    }

    private CreateClinicalPresetRequest request(String name) {
        return new CreateClinicalPresetRequest(name, "d",
                List.of(new PresetPoint(p1.getId(), s1.getId()), new PresetPoint(p2.getId(), s2.getId())));
    }

    // ---- findConfig ----

    @Test
    @DisplayName("Config numera sensor só nos dois primeiros pontos e mapeia o cenário escolhido")
    void findConfigSensorIndexes() {
        when(points.findByAnimalIdOrderByPositionAsc(animal.getId())).thenReturn(List.of(p1, p2, p3));
        DeviceScenarioModel chosen = DeviceScenarioModel.builder().device(device).point(p2).scenario(s2).build();
        when(selections.findByDeviceId(device.getId())).thenReturn(List.of(chosen));

        DeviceConfig config = service.findConfig(device);

        assertEquals(device.getId(), config.deviceId());
        assertEquals(3, config.points().size());
        assertEquals(1, config.points().get(0).sensorIndex());
        assertEquals(2, config.points().get(1).sensorIndex());
        assertNull(config.points().get(2).sensorIndex());
        assertNull(config.points().get(0).scenario());
        assertEquals(s2.getId(), config.points().get(1).scenario().id());
        assertTrue(config.points().get(1).scenario().audioUri().endsWith(s2.getId() + "/audio"));
    }

    // ---- select ----

    @Test
    @DisplayName("Seleção recusa ponto sem animal e cenário sem ponto")
    void selectRejectsOrphans() {
        AuscultationPointModel orphanPoint = AuscultationPointModel.builder().id(UUID.randomUUID()).build();
        when(points.findById(orphanPoint.getId())).thenReturn(Optional.of(orphanPoint));
        UUID deviceId = device.getId();

        assertThrows(InvalidDeviceUpdateException.class,
                () -> service.select(admin, deviceId, orphanPoint.getId(), s1.getId()));

        ScenarioModel orphanScenario = ScenarioModel.builder().id(UUID.randomUUID()).build();
        when(points.findById(p1.getId())).thenReturn(Optional.of(p1));
        when(scenarios.findById(orphanScenario.getId())).thenReturn(Optional.of(orphanScenario));

        assertThrows(InvalidDeviceUpdateException.class,
                () -> service.select(admin, deviceId, p1.getId(), orphanScenario.getId()));
        verify(selections, never()).save(any());
    }

    @Test
    @DisplayName("Seleção de ponto inexistente lança AuscultationPointNotFound")
    void selectPointMissing() {
        UUID missing = UUID.randomUUID();
        when(points.findById(missing)).thenReturn(Optional.empty());
        UUID deviceId = device.getId();

        assertThrows(AuscultationPointNotFound.class, () -> service.select(admin, deviceId, missing, s1.getId()));
    }

    @Test
    @DisplayName("Seleção de cenário inexistente lança ScenarioNotFoundException")
    void selectScenarioMissing() {
        when(points.findById(p1.getId())).thenReturn(Optional.of(p1));
        UUID missing = UUID.randomUUID();
        when(scenarios.findById(missing)).thenReturn(Optional.empty());
        UUID deviceId = device.getId();

        assertThrows(ScenarioNotFoundException.class, () -> service.select(admin, deviceId, p1.getId(), missing));
    }

    // ---- createPreset ----

    @Test
    @DisplayName("Criar caso exige dispositivo ativo e com animal")
    void createPresetRequiresActiveWithAnimal() {
        UUID deviceId = device.getId();
        device.setStatus(DeviceStatus.INACTIVE);
        assertThrows(InvalidDeviceUpdateException.class, () -> service.createPreset(admin, deviceId, request("x")));

        device.setStatus(DeviceStatus.ACTIVE);
        device.setAnimal(null);
        assertThrows(InvalidDeviceUpdateException.class, () -> service.createPreset(admin, deviceId, request("x")));
        verify(presets, never()).save(any());
    }

    @Test
    @DisplayName("Criar caso com ponto repetido é recusado")
    void createPresetDuplicatePoint() {
        CreateClinicalPresetRequest duplicated = new CreateClinicalPresetRequest("x", null,
                List.of(new PresetPoint(p1.getId(), s1.getId()), new PresetPoint(p1.getId(), s2.getId())));
        UUID deviceId = device.getId();

        InvalidDeviceUpdateException exception = assertThrows(InvalidDeviceUpdateException.class,
                () -> service.createPreset(admin, deviceId, duplicated));
        assertTrue(exception.getMessage().contains("repetido"));
    }

    @Test
    @DisplayName("Criar caso com nome já existente para o animal é recusado")
    void createPresetDuplicateName() {
        when(presets.existsByAnimalIdAndNameIgnoreCase(animal.getId(), "Caso")).thenReturn(true);
        UUID deviceId = device.getId();

        assertThrows(InvalidDeviceUpdateException.class,
                () -> service.createPreset(admin, deviceId, request("  Caso ")));
        verify(presets, never()).save(any());
    }

    @Test
    @DisplayName("Criar caso grava nome sem espaços e seleções na ordem informada")
    void createPresetSavesTrimmed() {
        when(presets.save(any())).thenAnswer(inv -> {
            ClinicalScenarioPresetModel preset = inv.getArgument(0);
            preset.setId(UUID.randomUUID());
            return preset;
        });

        ClinicalPresetInfo info = service.createPreset(admin, device.getId(), request("  Caso "));

        ArgumentCaptor<ClinicalScenarioPresetModel> captor = ArgumentCaptor.forClass(ClinicalScenarioPresetModel.class);
        verify(presets).save(captor.capture());
        assertEquals("Caso", captor.getValue().getName());
        assertSame(animal, captor.getValue().getAnimal());
        assertEquals("Caso", info.name());
        assertEquals(p1.getId(), info.points().get(0).pointId());
        assertEquals(s2.getId(), info.points().get(1).scenarioId());
    }

    @Test
    @DisplayName("Caso com cenário inexistente, sem ponto ou sem pontos ativos é recusado")
    void createPresetResolveFailures() {
        UUID deviceId = device.getId();

        when(scenarios.findAllById(any())).thenReturn(List.of(s1));
        assertThrows(ScenarioNotFoundException.class, () -> service.createPreset(admin, deviceId, request("x")));

        ScenarioModel noPoint = ScenarioModel.builder().id(s2.getId()).name("N").build();
        when(scenarios.findAllById(any())).thenReturn(List.of(s1, noPoint));
        assertThrows(InvalidDeviceUpdateException.class, () -> service.createPreset(admin, deviceId, request("x")));

        when(points.findByAnimalIdOrderByPositionAsc(animal.getId())).thenReturn(List.of());
        assertThrows(InvalidDeviceUpdateException.class, () -> service.createPreset(admin, deviceId, request("x")));
    }

    @Test
    @DisplayName("Caso com cenário desativado é recusado citando o nome do cenário")
    void createPresetInactiveScenario() {
        s2.setActive(false);
        UUID deviceId = device.getId();

        InvalidDeviceUpdateException exception = assertThrows(InvalidDeviceUpdateException.class,
                () -> service.createPreset(admin, deviceId, request("x")));
        assertTrue(exception.getMessage().contains("S2"));
    }

    // ---- listPresets / deletePreset ----

    @Test
    @DisplayName("Listar casos devolve vazio sem animal e os casos do animal quando existe")
    void listPresets() {
        ClinicalScenarioPresetModel preset = new ClinicalScenarioPresetModel();
        preset.setId(UUID.randomUUID());
        preset.setName("Caso");
        preset.setSelections(full());
        when(presets.findByAnimalIdOrderByNameAsc(animal.getId())).thenReturn(List.of(preset));

        List<ClinicalPresetInfo> infos = service.listPresets(admin, device.getId());
        assertEquals(1, infos.size());
        assertEquals(2, infos.get(0).points().size());

        device.setAnimal(null);
        assertTrue(service.listPresets(admin, device.getId()).isEmpty());
    }

    @Test
    @DisplayName("Excluir caso sem animal ou inexistente responde 404")
    void deletePresetNotFound() {
        UUID presetId = UUID.randomUUID();
        UUID deviceId = device.getId();
        when(presets.findByIdAndAnimalId(presetId, animal.getId())).thenReturn(Optional.empty());

        ResponseStatusException missing = assertThrows(ResponseStatusException.class,
                () -> service.deletePreset(admin, deviceId, presetId));
        assertEquals(404, missing.getStatusCode().value());

        device.setAnimal(null);
        ResponseStatusException noAnimal = assertThrows(ResponseStatusException.class,
                () -> service.deletePreset(admin, deviceId, presetId));
        assertEquals(404, noAnimal.getStatusCode().value());
        verify(presets, never()).delete(any());
    }

    // ---- activatePreset ----

    @Test
    @DisplayName("Ativar caso inexistente responde 404 e dispositivo inativo é recusado")
    void activatePresetFailures() {
        UUID presetId = UUID.randomUUID();
        UUID deviceId = device.getId();
        when(presets.findByIdAndAnimalId(presetId, animal.getId())).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> service.activatePreset(admin, deviceId, presetId));

        device.setStatus(DeviceStatus.PENDING);
        assertThrows(InvalidDeviceUpdateException.class, () -> service.activatePreset(admin, deviceId, presetId));
        verify(selections, never()).saveAll(any());
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("Ativar caso atualiza linhas existentes e cria as que faltam, numa única gravação")
    void activatePresetUpdatesAndCreates() {
        ClinicalScenarioPresetModel preset = new ClinicalScenarioPresetModel();
        preset.setSelections(full());
        UUID presetId = UUID.randomUUID();
        when(presets.findByIdAndAnimalId(presetId, animal.getId())).thenReturn(Optional.of(preset));
        DeviceScenarioModel existing = DeviceScenarioModel.builder().device(device).point(p1).scenario(s3).build();
        when(selections.findByDeviceId(device.getId())).thenReturn(List.of(existing));

        DeviceModel result = service.activatePreset(admin, device.getId(), presetId);

        assertSame(device, result);
        ArgumentCaptor<List<DeviceScenarioModel>> captor = ArgumentCaptor.forClass(List.class);
        verify(selections).saveAll(captor.capture());
        List<DeviceScenarioModel> saved = captor.getValue();
        assertEquals(2, saved.size());
        assertSame(existing, saved.get(0));
        assertEquals(s1, existing.getScenario());
        assertEquals(p2, saved.get(1).getPoint());
        assertEquals(s2, saved.get(1).getScenario());
        assertSame(device, saved.get(1).getDevice());
    }

    // ---- DTOs ----

    @Test
    @DisplayName("ScenarioRef.from gera revisão estável a partir do áudio, ou do id quando não há áudio")
    void scenarioRefRevision() {
        ScenarioModel withAudio = ScenarioModel.builder().id(UUID.randomUUID()).name("A").description("d")
                .audioUrl("audio/a.mp3").build();
        ScenarioModel blankAudio = ScenarioModel.builder().id(UUID.randomUUID()).name("B").audioUrl("  ").build();
        ScenarioModel noAudio = ScenarioModel.builder().id(blankAudio.getId()).name("B").build();

        ScenarioRef first = ScenarioRef.from(withAudio);
        assertEquals(first.audioRevision(), ScenarioRef.from(withAudio).audioRevision());
        assertEquals("/api/v1/scenarios/" + withAudio.getId() + "/audio", first.audioUri());
        assertEquals("d", first.description());
        assertEquals(ScenarioRef.from(noAudio).audioRevision(), ScenarioRef.from(blankAudio).audioRevision());
        assertTrue(!first.audioRevision().equals(ScenarioRef.from(blankAudio).audioRevision()));
    }

    @Test
    @DisplayName("Construtores de compatibilidade de ScenarioRef e PointConfig aplicam os padrões")
    void compatibilityConstructors() {
        UUID id = UUID.randomUUID();
        ScenarioRef ref = new ScenarioRef(id, "n", "d", "uri");
        assertNull(ref.audioRevision());

        PointConfig four = new PointConfig(id, "01", 1, ref);
        PointConfig three = new PointConfig(id, "02", ref);
        assertTrue(four.active());
        assertEquals(1, four.sensorIndex());
        assertTrue(three.active());
        assertNull(three.sensorIndex());
        assertSame(ref, three.scenario());
        assertEquals(id, new SelectScenarioRequest(id).scenarioId());
    }

    // ---- Controller ----

    private DeviceScenarioController controller(DeviceScenarioService svc) {
        return new DeviceScenarioController(svc);
    }

    private DeviceModel operable() {
        ClassroomModel classroom = ClassroomModel.builder().id(UUID.randomUUID()).institution(institution).build();
        DeviceModel d = DeviceModel.builder().id(UUID.randomUUID()).status(DeviceStatus.ACTIVE)
                .institution(institution).build();
        d.setClassroom(classroom);
        return d;
    }

    @Test
    @DisplayName("Dispositivo operável recebe a configuração e a autorização de reprodução (204)")
    void controllerOperableDevice() {
        DeviceScenarioService svc = mock(DeviceScenarioService.class);
        DeviceModel d = operable();
        DeviceConfig config = new DeviceConfig(d.getId(), List.of());
        when(svc.findConfig(d)).thenReturn(config);

        assertSame(config, controller(svc).findMyConfig(d).getBody());
        assertEquals(204, controller(svc).authorizePlayback(d).getStatusCode().value());
    }

    @Test
    @DisplayName("Dispositivo sem condições de operar recebe AccessDenied em cada regra")
    void controllerDeniesInvalidDevices() {
        DeviceScenarioController controller = controller(mock(DeviceScenarioService.class));
        InstitutionModel inactive = InstitutionModel.builder().id(UUID.randomUUID()).active(false).build();
        InstitutionModel other = InstitutionModel.builder().id(UUID.randomUUID()).active(true).build();

        DeviceModel notActive = operable();
        notActive.setStatus(DeviceStatus.INACTIVE);
        DeviceModel noInstitution = operable();
        noInstitution.setInstitution(null);
        DeviceModel inactiveInstitution = operable();
        inactiveInstitution.setInstitution(inactive);
        DeviceModel noClassroom = operable();
        noClassroom.setClassroom(null);
        DeviceModel archived = operable();
        archived.getClassroom().setArchived(true);
        DeviceModel deleted = operable();
        deleted.getClassroom().setDeleted(true);
        DeviceModel classroomNoInstitution = operable();
        classroomNoInstitution.getClassroom().setInstitution(null);
        DeviceModel classroomOtherInstitution = operable();
        classroomOtherInstitution.getClassroom().setInstitution(other);

        assertThrows(AccessDeniedException.class, () -> controller.findMyConfig(null));
        assertThrows(AccessDeniedException.class, () -> controller.authorizePlayback(null));
        for (DeviceModel bad : List.of(notActive, noInstitution, inactiveInstitution, noClassroom, archived,
                deleted, classroomNoInstitution, classroomOtherInstitution)) {
            assertThrows(AccessDeniedException.class, () -> controller.authorizePlayback(bad));
        }
    }

    @Test
    @DisplayName("Endpoints do painel delegam ao service com os status HTTP esperados")
    void controllerPanelEndpoints() {
        DeviceScenarioService svc = mock(DeviceScenarioService.class);
        DeviceScenarioController controller = controller(svc);
        UUID id = UUID.randomUUID(), pointId = UUID.randomUUID(), presetId = UUID.randomUUID();
        DeviceConfig config = new DeviceConfig(id, List.of());
        ClinicalPresetInfo info = new ClinicalPresetInfo(presetId, "n", "d", List.of());
        CreateClinicalPresetRequest create = new CreateClinicalPresetRequest("n", "d",
                List.of(new PresetPoint(pointId, id)));
        when(svc.findConfig(admin, id)).thenReturn(config);
        when(svc.createPreset(admin, id, create)).thenReturn(info);
        when(svc.listPresets(admin, id)).thenReturn(List.of(info));

        assertSame(config, controller.findConfig(id, admin).getBody());
        assertEquals(204, controller.select(id, pointId, new SelectScenarioRequest(presetId), admin)
                .getStatusCode().value());
        verify(svc).select(admin, id, pointId, presetId);
        ResponseEntity<ClinicalPresetInfo> created = controller.createPreset(id, create, admin);
        assertEquals(201, created.getStatusCode().value());
        assertSame(info, created.getBody());
        assertEquals(List.of(info), controller.listPresets(id, admin).getBody());
        assertEquals(204, controller.deletePreset(id, presetId, admin).getStatusCode().value());
        verify(svc).deletePreset(admin, id, presetId);
        assertEquals(204, controller.activatePreset(id, presetId, admin).getStatusCode().value());
        verify(svc).activatePreset(admin, id, presetId);
    }
}
