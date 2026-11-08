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
  public DeviceModel findDeviceByMac(String mac) {
    String normalizedMac = mac != null ? mac.trim() : null;
    if (normalizedMac == null || normalizedMac.isBlank())
      throw new DeviceNotFoundException();

    return deviceRepository
        .findByMac(normalizedMac)
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
  public List<DeviceModel> findDevicesByName(String name, int page, int size) {
    return deviceRepository
        .findByNameContaining(
            name,
            PageRequest.of(page, size, Sort.by("name")))
        .toList();
  }
}
