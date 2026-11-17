package com.n0hana.echoes_server.device;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.n0hana.echoes_server.device.exception.DeviceNotFoundException;
import com.n0hana.echoes_server.device.port.DeviceScopeResolver;
import com.n0hana.echoes_server.user.model.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceRepository deviceRepository;

    private final DeviceScopeResolver scopeResolver;

    @Transactional(readOnly = true)
    public DeviceModel findDeviceByHardwareId(String hardwareId) {
        String normalizedHardwareId = hardwareId != null ? hardwareId.trim() : null;
        if (normalizedHardwareId == null || normalizedHardwareId.isBlank())
            throw new DeviceNotFoundException();

        return deviceRepository
                .findByHardwareId(normalizedHardwareId)
                .orElseThrow(DeviceNotFoundException::new);
    }

    /**
     * Busca um dispositivo por id.
     * 
     * <p>
     * Analisa se o usuário está no escopo da instituição para visualizar o
     * dispositivo e retorna o disposstivo identificado pelo ID
     * </p>
     * 
     * @param id   Identificador do dispositivo
     * @param user Usuário autenticado
     * @return {@link DeviceModel} identificado.
     */
    @Transactional(readOnly = true)
    public DeviceModel findDeviceById(UUID id, User user) {
        DeviceScope scope = scopeResolver.resolve(user);

        DeviceModel device = findDeviceById(id);

        if (scope instanceof DeviceScope.InstitutionScope institutionScope) {
            if (device.getStatus() == DeviceStatus.PENDING ||
                    device.getInstitution() == null ||
                    !institutionScope.institutionId()
                            .equals(device.getInstitution().getId())) {
                throw new DeviceNotFoundException();
            }
        }

        return device;
    }

    @Transactional(readOnly = true)
    public DeviceModel findDeviceById(UUID id) {
        return deviceRepository
                .findById(id)
                .orElseThrow(DeviceNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public List<DeviceModel> findAllDevices(int page, int size) {
        return deviceRepository
                .findAll(PageRequest.of(page, size, Sort.by("name")))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DeviceModel> findDevicesByStatus(DeviceStatus status, int page, int size) {
        return deviceRepository
                .findByStatus(status, PageRequest.of(page, size, Sort.by("name")))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DeviceModel> findDevicesByName(String name, int page, int size) {
        return deviceRepository
                .findByNameContaining(
                        name,
                        PageRequest.of(page, size, Sort.by("name")))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<DeviceModel> findDevices(User user, String name, DeviceStatus status, UUID institutionId,
            Pageable pageable) {
        DeviceScope scope = scopeResolver.resolve(user);

        UUID effectiveInstitutionId = scope.effectiveInstitution(institutionId);

        boolean includePending = scope instanceof DeviceScope.All;

        if (name != null && name.isBlank()) {
            name = null;
        }

        return deviceRepository.findDevices(name, status, effectiveInstitutionId, includePending, DeviceStatus.PENDING,
                pageable);
    }
}
