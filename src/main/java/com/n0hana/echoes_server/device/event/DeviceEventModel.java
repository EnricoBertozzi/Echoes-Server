package com.n0hana.echoes_server.device.event;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Evento de uso de um dispositivo.
 *
 * <p>
 * Os ids de dispositivo, instituição e cenário ficam como UUID simples, sem
 * chave estrangeira: o histórico de uso sobrevive à exclusão do dispositivo ou
 * do cenário, e a instituição gravada é a que valia no momento do evento
 * (mover o dispositivo depois não reescreve o passado).
 * </p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "device_event_tb", indexes = {
    @Index(name = "idx_device_event_institution_day", columnList = "institution_id, occurred_on"),
    @Index(name = "idx_device_event_device", columnList = "device_id, occurred_at")
})
public class DeviceEventModel {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "device_id", nullable = false)
  private UUID deviceId;

  @Column(name = "institution_id", nullable = false)
  private UUID institutionId;

  @Column(name = "scenario_id")
  private UUID scenarioId;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false, length = 20)
  private DeviceEventType type;

  @Column(name = "occurred_at", nullable = false)
  private LocalDateTime occurredAt;

  /** Dia do evento, gravado à parte para agrupar por dia em qualquer banco. */
  @Column(name = "occurred_on", nullable = false)
  private LocalDate occurredOn;

  public static DeviceEventModel from(DeviceUsageEvent event) {
    DeviceEventModel model = new DeviceEventModel();
    model.setDeviceId(event.deviceId());
    model.setInstitutionId(event.institutionId());
    model.setScenarioId(event.scenarioId());
    model.setType(event.type());
    model.setOccurredAt(event.occurredAt());
    model.setOccurredOn(event.occurredAt().toLocalDate());
    return model;
  }
}
