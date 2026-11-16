package com.n0hana.echoes_server.device;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.n0hana.echoes_server.animal.AnimalModel;
import com.n0hana.echoes_server.auscultation.AuscultationPointModel;
import com.n0hana.echoes_server.auscultation.AuscultationPointRepository;
import com.n0hana.echoes_server.auscultation.exception.AuscultationPointNotFound;
import com.n0hana.echoes_server.classroom.ClassroomMemberRepository;
import com.n0hana.echoes_server.classroom.ClassroomModel;
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
import com.n0hana.echoes_server.user.model.UserRole;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DeviceScenarioService {

  private final DeviceRepository deviceRepository;
  private final AuscultationPointRepository pointRepository;
  private final ScenarioRepository scenarioRepository;
  private final DeviceScenarioRepository repository;
  private final DeviceScopeResolver scopeResolver;
  private final ClassroomMemberRepository classroomMemberRepository;

  @Transactional(readOnly = true)
  public DeviceConfig findConfig(DeviceModel device) {
    AnimalModel animal = device.getAnimal();

    if (animal == null) {
      return new DeviceConfig(device.getId(), List.of());
    }

    Map<UUID, ScenarioModel> chosen = repository.findByDeviceId(device.getId())
        .stream()
        .collect(Collectors.toMap(
            selection -> selection.getPoint().getId(),
            DeviceScenarioModel::getScenario,
            (a, b) -> a));

    List<AuscultationPointModel> points = pointRepository.findByAnimalIdOrderByPositionAsc(animal.getId());

    List<PointConfig> result = IntStream.range(0, points.size())
        .mapToObj(index -> {
          AuscultationPointModel point = points.get(index);

          // Os dois primeiros pontos ordenados por posição mapeiam direto para FSR 1 e FSR 2
          Integer sensorIndex = index < 2 ? index + 1 : null;
          ScenarioModel scenario = chosen.get(point.getId());
          ScenarioRef reference = scenario == null ? null : ScenarioRef.from(scenario);

          return new PointConfig(
              point.getId(),
              point.getPosition(),
              sensorIndex,
              reference);
        })
        .toList();

    return new DeviceConfig(device.getId(), result);
  }

  @Transactional(readOnly = true)
  public DeviceConfig findConfig(User user, UUID deviceId) {
    DeviceModel device = findDevice(deviceId);
    assertCanManage(user, device);
    return findConfig(device);
  }

  @Transactional
  @Auditable(
      action = "UPDATE",
      entity = "DeviceScenario",
      entityId = "#deviceId",
      institutionId = "#result.device.institution.id")
  public DeviceScenarioModel select(
      User user,
      UUID deviceId,
      UUID pointId,
      UUID scenarioId) {
    DeviceModel device = findDevice(deviceId);
    assertCanManage(user, device);

    if (device.getStatus() != DeviceStatus.ACTIVE) {
      throw new InvalidDeviceUpdateException("O dispositivo deve estar ativo");
    }

    if (device.getAnimal() == null) {
      throw new InvalidDeviceUpdateException("Dispositivo sem animal associado");
    }

    AuscultationPointModel point = pointRepository.findById(pointId)
        .orElseThrow(AuscultationPointNotFound::new);

    if (point.getAnimal() == null || !point.getAnimal().getId().equals(device.getAnimal().getId())) {
      throw new InvalidDeviceUpdateException("O ponto não pertence ao animal do dispositivo");
    }

    List<AuscultationPointModel> physicalPoints = pointRepository
        .findByAnimalIdOrderByPositionAsc(device.getAnimal().getId());

    boolean mapped = physicalPoints.stream()
        .limit(2)
        .anyMatch(p -> p.getId().equals(pointId));

    if (!mapped) {
      throw new InvalidDeviceUpdateException(
          "O dispositivo possui apenas dois sensores FSR. Escolha um dos dois primeiros pontos do animal.");
    }

    ScenarioModel scenario = scenarioRepository.findById(scenarioId)
        .orElseThrow(ScenarioNotFoundException::new);

    if (scenario.getAuscultationPoint() == null || !scenario.getAuscultationPoint().getId().equals(pointId)) {
      throw new InvalidDeviceUpdateException("O cenário não pertence a este ponto");
    }

    DeviceScenarioModel selection = repository.findByDeviceIdAndPointId(deviceId, pointId)
        .orElseGet(() -> DeviceScenarioModel.builder()
            .device(device)
            .point(point)
            .build());

    selection.setScenario(scenario);

    return repository.save(selection);
  }

  private DeviceModel findDevice(UUID deviceId) {
    return deviceRepository.findById(deviceId)
        .orElseThrow(DeviceNotFoundException::new);
  }

  private void assertCanManage(User user, DeviceModel device) {
    if (user == null) {
      throw new AccessDeniedException("Usuário não autenticado");
    }

    if (device.getInstitution() == null || !device.getInstitution().isActive()) {
      throw new AccessDeniedException("Instituição ausente ou inativa");
    }

    DeviceScope scope = scopeResolver.resolve(user);
    scope.effectiveInstitution(device.getInstitution().getId());

    if (user.getUserRole() == UserRole.TEACHER) {
      ClassroomModel classroom = device.getClassroom();

      if (classroom == null
          || classroom.isArchived()
          || classroom.isDeleted()
          || classroom.getInstitution() == null
          || !classroom.getInstitution().getId().equals(device.getInstitution().getId())) {
        throw new AccessDeniedException("Dispositivo sem turma ativa autorizada");
      }

      boolean isMember = classroomMemberRepository.existsByClassroomIdAndUserId(
          classroom.getId(),
          user.getId());

      if (!isMember) {
        throw new AccessDeniedException("Professor não pertence à turma do dispositivo");
      }
    }
  }
}
