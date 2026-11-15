package com.n0hana.echoes_server.device;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.n0hana.echoes_server.animal.AnimalModel;
import com.n0hana.echoes_server.animal.AnimalRepository;
import com.n0hana.echoes_server.animal.exception.AnimalNotFoundException;
import com.n0hana.echoes_server.device.DeviceDTO.UpdateDeviceRequest;
import com.n0hana.echoes_server.device.exception.DeviceNotFoundException;
import com.n0hana.echoes_server.device.exception.InvalidDeviceStateException;
import com.n0hana.echoes_server.device.exception.InvalidDeviceUpdateException;
import com.n0hana.echoes_server.infra.logs.Auditable;
import com.n0hana.echoes_server.institution.InstitutionModel;
import com.n0hana.echoes_server.institution.InstitutionRepository;
import com.n0hana.echoes_server.institution.exception.InstitutionNotFoundException;

import lombok.RequiredArgsConstructor;

/**
 * Regras de ciclo de vida do dispositivo: aprovar, editar (nome, instituição e
 * animal), ativar, inativar e excluir. Chamado apenas por rotas de ADMIN.
 * Mudanças de status publicam {@link DeviceStatusChanged}.
 *
 * <p>
 * Transições: PENDING → ACTIVE (aprovar, com instituição); ACTIVE → INACTIVE
 * (inativar); INACTIVE → ACTIVE (ativar). A edição de nome/animal vale em
 * qualquer estado; a troca de instituição exige dispositivo já aprovado. A
 * exclusão vale em qualquer estado.
 * </p>
 */
@Service
@RequiredArgsConstructor
public class DeviceLifecycleService {

  private static final int MAX_NAME_LENGTH = 255;

  private final DeviceRepository deviceRepository;
  private final InstitutionRepository institutionRepository;
  private final AnimalRepository animalRepository;
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
    return update(deviceId, new UpdateDeviceRequest(null, institutionId, null));
  }

  /**
   * Atualização parcial do dispositivo: nome, instituição e animal. Apenas os
   * campos enviados são alterados; omitir um campo mantém o valor atual.
   *
   * <p>
   * A troca de instituição segue a mesma regra de {@link #changeInstitution}:
   * dispositivo {@code PENDING} precisa ser aprovado antes de ser movido. O
   * vínculo com animal pode ser alterado em qualquer estado, mas, uma vez
   * definido, não pode ser removido: omitir {@code animalId} mantém o animal
   * atual.
   * </p>
   */
  @Transactional
  @Auditable(action = "UPDATE", entity = "Device", entityId = "#deviceId", institutionId = "#result.institution?.id")
  public DeviceModel update(UUID deviceId, UpdateDeviceRequest request) {
    boolean hasName = request != null && request.name() != null;
    boolean hasInstitution = request != null && request.institutionId() != null;
    boolean hasAnimal = request != null && request.animalId() != null;

    if (!hasName && !hasInstitution && !hasAnimal)
      throw new InvalidDeviceUpdateException("Informe ao menos um campo para atualizar");

    String normalizedName = hasName ? normalizeName(request.name()) : null;

    DeviceModel device = findDevice(deviceId);

    if (normalizedName != null)
      device.setName(normalizedName);

    if (hasInstitution) {
      if (device.getStatus() == DeviceStatus.PENDING)
        throw new InvalidDeviceStateException("Dispositivo pendente deve ser aprovado, não movido");

      device.setInstitution(findInstitution(request.institutionId()));
    }

    if (hasAnimal)
      device.setAnimal(findAnimal(request.animalId()));

    return deviceRepository.save(device);
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

  private AnimalModel findAnimal(UUID id) {
    return animalRepository.findById(id)
        .orElseThrow(AnimalNotFoundException::new);
  }

  private String normalizeName(String name) {
    String normalized = name.trim();

    if (normalized.isEmpty())
      throw new InvalidDeviceUpdateException("Nome não pode ser vazio");

    if (normalized.length() > MAX_NAME_LENGTH)
      throw new InvalidDeviceUpdateException("Nome deve ter no máximo " + MAX_NAME_LENGTH + " caracteres");

    return normalized;
  }
}
