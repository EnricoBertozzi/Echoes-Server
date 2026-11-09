package com.n0hana.echoes_server.device;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.n0hana.echoes_server.device.exception.DeviceNotFoundException;
import com.n0hana.echoes_server.device.exception.InvalidDeviceStateException;
import com.n0hana.echoes_server.infra.logs.Auditable;
import com.n0hana.echoes_server.institution.InstitutionModel;
import com.n0hana.echoes_server.institution.InstitutionRepository;
import com.n0hana.echoes_server.institution.exception.InstitutionNotFoundException;

import lombok.RequiredArgsConstructor;

/**
 * Regras de ciclo de vida do dispositivo: aprovar, mover de instituição,
 * ativar, inativar e excluir. Chamado apenas por rotas de ADMIN. Cada mudança
 * publica {@link DeviceStatusChanged}.
 *
 * <p>
 * Transições: PENDING → ACTIVE (aprovar, com instituição); ACTIVE → INACTIVE
 * (inativar); INACTIVE → ACTIVE (ativar). A exclusão vale em qualquer estado.
 * </p>
 */
@Service
@RequiredArgsConstructor
public class DeviceLifecycleService {

  private final DeviceRepository deviceRepository;
  private final InstitutionRepository institutionRepository;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional
  @Auditable(action = "APPROVE", entity = "Device", entityId = "#deviceId", institutionId = "#institutionId")
  public DeviceModel approve(UUID deviceId, UUID institutionId, UUID approverId) {
    DeviceModel device = findDevice(deviceId);

    if (device.getStatus() != DeviceStatus.PENDING)
      throw new InvalidDeviceStateException("Apenas dispositivos pendentes podem ser aprovados");

    device.setInstitution(findInstitution(institutionId));
    device.setStatus(DeviceStatus.ACTIVE);
    device.setApprovedBy(approverId);
    device.setApprovedAt(LocalDateTime.now());

    return saveAndNotify(device);
  }

  @Transactional
  @Auditable(action = "CHANGE_INSTITUTION", entity = "Device", entityId = "#deviceId", institutionId = "#institutionId")
  public DeviceModel changeInstitution(UUID deviceId, UUID institutionId) {
    DeviceModel device = findDevice(deviceId);

    if (device.getStatus() == DeviceStatus.PENDING)
      throw new InvalidDeviceStateException("Dispositivo pendente deve ser aprovado, não movido");

    device.setInstitution(findInstitution(institutionId));

    return saveAndNotify(device);
  }

  @Transactional
  @Auditable(action = "ACTIVATE", entity = "Device", entityId = "#deviceId", institutionId = "#result.institution?.id")
  public DeviceModel activate(UUID deviceId) {
    DeviceModel device = findDevice(deviceId);

    if (device.getStatus() != DeviceStatus.INACTIVE)
      throw new InvalidDeviceStateException("Apenas dispositivos inativos podem ser ativados");

    device.setStatus(DeviceStatus.ACTIVE);

    return saveAndNotify(device);
  }

  @Transactional
  @Auditable(action = "DEACTIVATE", entity = "Device", entityId = "#deviceId", institutionId = "#result.institution?.id")
  public DeviceModel deactivate(UUID deviceId) {
    DeviceModel device = findDevice(deviceId);

    if (device.getStatus() != DeviceStatus.ACTIVE)
      throw new InvalidDeviceStateException("Apenas dispositivos ativos podem ser inativados");

    device.setStatus(DeviceStatus.INACTIVE);

    return saveAndNotify(device);
  }

  @Transactional
  @Auditable(action = "DELETE", entity = "Device", entityId = "#deviceId", institutionId = "#result.institution?.id")
  public DeviceModel delete(UUID deviceId) {
    DeviceModel device = findDevice(deviceId);

    deviceRepository.delete(device);
    return device; // devolvido para a auditoria registrar a instituição
  }

  private DeviceModel saveAndNotify(DeviceModel device) {
    DeviceModel saved = deviceRepository.save(device);
    eventPublisher.publishEvent(new DeviceStatusChanged(saved.getId(), saved.getStatus()));
    return saved;
  }

  private DeviceModel findDevice(UUID id) {
    return deviceRepository.findById(id).orElseThrow(DeviceNotFoundException::new);
  }

  private InstitutionModel findInstitution(UUID id) {
    return institutionRepository.findById(id)
        .orElseThrow(() -> new InstitutionNotFoundException("Instituição não encontrada."));
  }
}
