package com.n0hana.echoes_server.device.event;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.n0hana.echoes_server.device.DeviceRepository;
import com.n0hana.echoes_server.device.port.DeviceUsageRecorder;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JpaDeviceUsageRecorder implements DeviceUsageRecorder {

  private final DeviceEventRepository eventRepository;
  private final DeviceRepository deviceRepository;

  @Override
  @Transactional
  public void record(DeviceUsageEvent event) {
    eventRepository.save(DeviceEventModel.from(event));
    deviceRepository.touchLastSeen(event.deviceId(), event.occurredAt());
  }
}
