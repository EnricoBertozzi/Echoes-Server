package com.n0hana.echoes_server.device;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.n0hana.echoes_server.animal.AnimalModel;
import com.n0hana.echoes_server.auscultation.AuscultationPointModel;
import com.n0hana.echoes_server.auscultation.AuscultationPointRepository;
import com.n0hana.echoes_server.auscultation.exception.AuscultationPointNotFound;
import com.n0hana.echoes_server.device.DeviceScenarioDTO.DeviceConfig;
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

    List<PointConfig> points = pointRepository.findByAnimalIdOrderByPositionAsc(animal.getId()).stream()
        .map(point -> new PointConfig(
            point.getId(),
            point.getPosition(),
            chosen.containsKey(point.getId()) ? ScenarioRef.from(chosen.get(point.getId())) : null))
        .toList();

    return new DeviceConfig(device.getId(), points);
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
