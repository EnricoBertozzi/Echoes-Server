package com.n0hana.echoes_server.device;

import org.springframework.stereotype.Component;

import com.n0hana.echoes_server.device.port.DeviceAccessPolicy;
import com.n0hana.echoes_server.institution.InstitutionModel;

/**
 * Pode operar quando o dispositivo está ACTIVE e a instituição está ativa.
 * O estado é calculado a cada consulta: inativar a instituição não altera os
 * dispositivos, e reativá-la os libera de volta.
 */
@Component
public class DefaultDeviceAccessPolicy implements DeviceAccessPolicy {

  @Override
  public boolean canOperate(DeviceModel device) {
    if (device == null || device.getStatus() != DeviceStatus.ACTIVE)
      return false;

    InstitutionModel institution = device.getInstitution();

    return institution != null && institution.isActive();
  }
}
