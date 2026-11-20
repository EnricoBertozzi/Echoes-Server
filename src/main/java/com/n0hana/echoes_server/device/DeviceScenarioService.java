package com.n0hana.echoes_server.device;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.HashMap;
import java.util.Set;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.n0hana.echoes_server.animal.AnimalModel;
import com.n0hana.echoes_server.auscultation.AuscultationPointModel;
import com.n0hana.echoes_server.auscultation.AuscultationPointRepository;
import com.n0hana.echoes_server.auscultation.exception.AuscultationPointNotFound;
import com.n0hana.echoes_server.device.DeviceScenarioDTO.DeviceConfig;
import com.n0hana.echoes_server.device.DeviceScenarioDTO.ClinicalPresetInfo;
import com.n0hana.echoes_server.device.DeviceScenarioDTO.CreateClinicalPresetRequest;
import com.n0hana.echoes_server.device.DeviceScenarioDTO.PresetPoint;
import com.n0hana.echoes_server.device.DeviceScenarioDTO.PointConfig;
import com.n0hana.echoes_server.device.DeviceScenarioDTO.ScenarioRef;
import com.n0hana.echoes_server.device.exception.DeviceNotFoundException;
import com.n0hana.echoes_server.device.exception.InvalidDeviceUpdateException;
import com.n0hana.echoes_server.device.port.DeviceScopeResolver;
import com.n0hana.echoes_server.infra.logs.Auditable;
import com.n0hana.echoes_server.scenario.ScenarioModel;
import com.n0hana.echoes_server.scenario.ScenarioRepository;
import com.n0hana.echoes_server.scenario.exception.ScenarioNotFoundException;
import com.n0hana.echoes_server.user.model.User;

import lombok.RequiredArgsConstructor;

/**
 * Escolha do cenário atual de cada ponto de ausculta de um dispositivo e a
 * leitura dessa configuração (pelo ESP e pelo painel).
 */
@Service
@RequiredArgsConstructor
public class DeviceScenarioService {

  private final DeviceRepository deviceRepository;
  private final AuscultationPointRepository pointRepository;
  private final ScenarioRepository scenarioRepository;
  private final DeviceScenarioRepository repository;
  private final ClinicalScenarioPresetRepository presetRepository;
  private final DeviceScopeResolver scopeResolver;

  /**
   * Pontos do animal do dispositivo, em ordem de posição, cada um com o cenário
   * atual (ou nulo). Dispositivo sem animal devolve lista vazia.
   */
  @Transactional(readOnly = true)
  public DeviceConfig findConfig(DeviceModel device) {
    AnimalModel animal = device.getAnimal();
    if (animal == null)
      return new DeviceConfig(device.getId(), List.of());

    Map<UUID, ScenarioModel> chosen = repository.findByDeviceId(device.getId()).stream()
        .collect(Collectors.toMap(s -> s.getPoint().getId(), DeviceScenarioModel::getScenario));

    List<AuscultationPointModel> points =
        pointRepository.findByAnimalIdOrderByPositionAsc(animal.getId());

    List<PointConfig> configs = IntStream.range(0, points.size())
        .mapToObj(index -> {
          AuscultationPointModel point = points.get(index);
          ScenarioModel selected = chosen.get(point.getId());

          return new PointConfig(
              point.getId(),
              point.getPosition(),
              index < 2 ? index + 1 : null,
              selected == null ? null : ScenarioRef.from(selected));
        })
        .toList();

    return new DeviceConfig(device.getId(), configs);
  }

  /** Mesma leitura, para o painel: admin vê tudo, gestor e professor só a própria instituição. */
  @Transactional(readOnly = true)
  public DeviceConfig findConfig(User user, UUID deviceId) {
    DeviceModel device = findDevice(deviceId);
    assertCanManage(user, device);
    return findConfig(device);
  }

  /**
   * Define o cenário atual de um ponto. O ponto precisa ser do animal do
   * dispositivo e o cenário precisa ser desse ponto; escolher de novo troca o
   * anterior.
   */
  @Transactional
  @Auditable(action = "UPDATE", entity = "DeviceScenario", entityId = "#deviceId", institutionId = "#result.device.institution.id")
  public DeviceScenarioModel select(User user, UUID deviceId, UUID pointId, UUID scenarioId) {
    DeviceModel device = findDevice(deviceId);
    assertCanManage(user, device);

    if (device.getAnimal() == null)
      throw new InvalidDeviceUpdateException("Dispositivo sem animal associado");

    AuscultationPointModel point = pointRepository.findById(pointId)
        .orElseThrow(AuscultationPointNotFound::new);
    if (point.getAnimal() == null || !point.getAnimal().getId().equals(device.getAnimal().getId()))
      throw new InvalidDeviceUpdateException("O ponto não pertence ao animal do dispositivo");

    ScenarioModel scenario = scenarioRepository.findById(scenarioId)
        .orElseThrow(ScenarioNotFoundException::new);
    if (scenario.getAuscultationPoint() == null || !scenario.getAuscultationPoint().getId().equals(pointId))
      throw new InvalidDeviceUpdateException("O cenário não pertence a este ponto");

    DeviceScenarioModel selection = repository.findByDeviceIdAndPointId(deviceId, pointId)
        .orElseGet(() -> DeviceScenarioModel.builder().device(device).point(point).build());
    selection.setScenario(scenario);
    return repository.save(selection);
  }


  /** Cria um caso completo, sem alterar a configuracao atual do ESP32. */
  @Transactional
  @Auditable(action = "CREATE", entity = "ClinicalPreset", entityId = "#result.id()", entityName = "#result.name()")
  public ClinicalPresetInfo createPreset(User user, UUID deviceId, CreateClinicalPresetRequest request) {
    DeviceModel device = findDevice(deviceId);
    assertCanManage(user, device);
    assertActiveAnimal(device);

    String name = request.name().trim();
    Map<UUID, UUID> selections = new LinkedHashMap<>();
    for (PresetPoint point : request.points()) {
      if (selections.putIfAbsent(point.pointId(), point.scenarioId()) != null) {
        throw new InvalidDeviceUpdateException("Ponto repetido no caso clinico");
      }
    }

    // Um caso incompleto ou com cenario do ponto errado nunca e salvo.
    resolveSelections(device, selections);
    if (presetRepository.existsByAnimalIdAndNameIgnoreCase(device.getAnimal().getId(), name)) {
      throw new InvalidDeviceUpdateException("Ja existe um caso clinico com este nome para o animal");
    }

    ClinicalScenarioPresetModel preset = new ClinicalScenarioPresetModel();
    preset.setAnimal(device.getAnimal());
    preset.setName(name);
    preset.setDescription(request.description());
    preset.setSelections(selections);
    return toPresetInfo(presetRepository.save(preset));
  }

  @Transactional(readOnly = true)
  public List<ClinicalPresetInfo> listPresets(User user, UUID deviceId) {
    DeviceModel device = findDevice(deviceId);
    assertCanManage(user, device);
    if (device.getAnimal() == null) {
      return List.of();
    }
    return presetRepository.findByAnimalIdOrderByNameAsc(device.getAnimal().getId())
        .stream().map(DeviceScenarioService::toPresetInfo).toList();
  }

  /** Troca TODOS os pontos em uma transacao; o manifesto do ESP nao muda de formato. */
  @Transactional
  @Auditable(action = "UPDATE", entity = "DeviceScenario",
      entityId = "#deviceId", institutionId = "#result.institution.id")
  public DeviceModel activatePreset(User user, UUID deviceId, UUID presetId) {
    DeviceModel device = findDevice(deviceId);
    assertCanManage(user, device);
    assertActiveAnimal(device);

    ClinicalScenarioPresetModel preset = presetRepository
        .findByIdAndAnimalId(presetId, device.getAnimal().getId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Caso clinico nao encontrado"));

    // Valida todos os pontos, audios e vinculos ANTES da primeira escrita.
    List<ResolvedSelection> desired = resolveSelections(device, preset.getSelections());
    Map<UUID, DeviceScenarioModel> current = new HashMap<>();
    for (DeviceScenarioModel selected : repository.findByDeviceId(deviceId)) {
      current.put(selected.getPoint().getId(), selected);
    }

    List<DeviceScenarioModel> updates = desired.stream().map(selection -> {
      DeviceScenarioModel row = current.get(selection.point().getId());
      if (row == null) {
        row = DeviceScenarioModel.builder().device(device).point(selection.point()).build();
      }
      row.setScenario(selection.scenario());
      return row;
    }).toList();

    repository.saveAll(updates);
    return device;
  }

  private void assertActiveAnimal(DeviceModel device) {
    if (device.getStatus() != DeviceStatus.ACTIVE) {
      throw new InvalidDeviceUpdateException("O dispositivo deve estar ativo");
    }
    if (device.getAnimal() == null) {
      throw new InvalidDeviceUpdateException("Dispositivo sem animal associado");
    }
  }

  private record ResolvedSelection(AuscultationPointModel point, ScenarioModel scenario) {}

  private List<ResolvedSelection> resolveSelections(DeviceModel device, Map<UUID, UUID> selections) {
    List<AuscultationPointModel> points = pointRepository
        .findByAnimalIdOrderByPositionAsc(device.getAnimal().getId());

    // Preset completo: cada ponto do animal deve ter exatamente um audio.
    Set<UUID> allPointIds = points.stream().map(AuscultationPointModel::getId)
        .collect(Collectors.toSet());
    if (points.isEmpty() || !allPointIds.equals(selections.keySet())) {
      throw new InvalidDeviceUpdateException("O caso clinico deve contemplar todos os pontos do animal");
    }

    Map<UUID, ScenarioModel> scenarios = new HashMap<>();
    for (ScenarioModel scenario : scenarioRepository.findAllById(selections.values())) {
      scenarios.put(scenario.getId(), scenario);
    }

    return points.stream().map(point -> {
      ScenarioModel scenario = scenarios.get(selections.get(point.getId()));
      if (scenario == null) {
        throw new ScenarioNotFoundException();
      }
      if (scenario.getAuscultationPoint() == null
          || !point.getId().equals(scenario.getAuscultationPoint().getId())) {
        throw new InvalidDeviceUpdateException("O cenario nao pertence ao ponto " + point.getPosition());
      }
      return new ResolvedSelection(point, scenario);
    }).toList();
  }

  private static ClinicalPresetInfo toPresetInfo(ClinicalScenarioPresetModel preset) {
    List<PresetPoint> points = preset.getSelections().entrySet().stream()
        .map(entry -> new PresetPoint(entry.getKey(), entry.getValue()))
        .toList();
    return new ClinicalPresetInfo(preset.getId(), preset.getName(), preset.getDescription(), points);
  }

  private DeviceModel findDevice(UUID deviceId) {
    return deviceRepository.findById(deviceId).orElseThrow(DeviceNotFoundException::new);
  }

  // gestor e professor só mexem em dispositivo da própria instituição
  private void assertCanManage(User user, DeviceModel device) {
    DeviceScope scope = scopeResolver.resolve(user);
    UUID institutionId = device.getInstitution() != null ? device.getInstitution().getId() : null;
    if (scope instanceof DeviceScope.InstitutionScope && institutionId == null)
      throw new AccessDeniedException("Dispositivo sem instituição");
    scope.effectiveInstitution(institutionId);
  }
}
