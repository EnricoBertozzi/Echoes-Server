package com.n0hana.echoes_server.device;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.n0hana.echoes_server.device.exception.DeviceNotFoundException;

@Service
public class DeviceService {

  @Autowired
  private DeviceRepository deviceRepository;

  @Transactional(readOnly = true)
  public DeviceModel findDeviceByHardwareId(String hardwareId) {
    String normalizedHardwareId = hardwareId != null ? hardwareId.trim() : null;
    if (normalizedHardwareId == null || normalizedHardwareId.isBlank())
      throw new DeviceNotFoundException();

    return deviceRepository
        .findByHardwareId(normalizedHardwareId)
        .orElseThrow(DeviceNotFoundException::new);
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
}
