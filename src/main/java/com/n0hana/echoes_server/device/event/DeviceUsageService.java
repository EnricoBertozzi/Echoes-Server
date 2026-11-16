package com.n0hana.echoes_server.device.event;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.n0hana.echoes_server.device.DeviceModel;
import com.n0hana.echoes_server.device.port.DeviceUsageRecorder;
import com.n0hana.echoes_server.institution.InstitutionModel;
import com.n0hana.echoes_server.scenario.ScenarioService;

import lombok.RequiredArgsConstructor;

/**
 * Valida e grava o evento de uso reportado por um dispositivo autenticado.
 *
 * <p>
 * Regras: o dispositivo precisa ter instituição (o filtro de segurança já
 * barra PENDING, INACTIVE e instituição inativa); CONNECTION não leva cenário;
 * PLAY, PAUSE e STOP exigem um cenário que pertença ao animal do dispositivo.
 * O horário é o do servidor, porque o relógio do ESP não é confiável.
 * </p>
 */
@Service
@RequiredArgsConstructor
public class DeviceUsageService {

  private final DeviceUsageRecorder recorder;
  private final ScenarioService scenarioService;

  public void report(DeviceModel device, DeviceEventRequest request) {
    if (device == null)
      throw new AccessDeniedException("Dispositivo não autenticado");

    InstitutionModel institution = device.getInstitution();
    if (institution == null)
      throw new AccessDeniedException("Dispositivo sem instituição");

    DeviceEventType type = request.type();
    UUID scenarioId = request.scenarioId();

    if (type == DeviceEventType.CONNECTION && scenarioId != null)
      throw new InvalidDeviceEventException("CONNECTION não aceita cenário");

    if (type != DeviceEventType.CONNECTION) {
      if (scenarioId == null)
        throw new InvalidDeviceEventException(type + " exige o cenário");

      // Lança se o cenário não existe ou não é do animal deste dispositivo
      scenarioService.findScenarioById(scenarioId, device);
    }

    recorder.record(new DeviceUsageEvent(
        device.getId(),
        institution.getId(),
        scenarioId,
        type,
        LocalDateTime.now()));
  }
}
