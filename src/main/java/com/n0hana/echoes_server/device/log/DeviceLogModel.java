package com.n0hana.echoes_server.device.log;

import java.time.Instant;
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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Log enviado pelo firmware. Fica em tabela própria (e não dentro do
 * dispositivo) porque cresce sem parar e é expurgado por prazo.
 *
 * <p>
 * Dispositivo e instituição são UUIDs simples, sem chave estrangeira: a
 * instituição gravada é a que valia no momento do log, então mover o
 * dispositivo não expõe o histórico à nova instituição. A chave única
 * (dispositivo, boot, seq) torna o reenvio de um lote idempotente.
 * </p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "device_log_tb", indexes = {
    @Index(name = "idx_device_log_device_time", columnList = "device_id, occurred_at"),
    @Index(name = "idx_device_log_occurred", columnList = "occurred_at")
}, uniqueConstraints = @UniqueConstraint(name = "uk_device_log_seq", columnNames = { "device_id", "boot_id", "seq" }))
public class DeviceLogModel {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "device_id", nullable = false)
  private UUID deviceId;

  @Column(name = "institution_id", nullable = false)
  private UUID institutionId;

  /** Identifica um boot do ESP; o contador {@code seq} recomeça a cada boot. */
  @Column(name = "boot_id", nullable = false, length = 8)
  private String bootId;

  @Column(name = "seq", nullable = false)
  private long seq;

  /** Horário do servidor menos a idade do evento informada pelo ESP. */
  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  @Column(name = "received_at", nullable = false)
  private Instant receivedAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "level", nullable = false, length = 8)
  private DeviceLogLevel level;

  @Enumerated(EnumType.STRING)
  @Column(name = "category", nullable = false, length = 12)
  private DeviceLogCategory category;

  @Column(name = "code", nullable = false, length = 40)
  private String code;

  @Column(name = "message", nullable = false, length = 300)
  private String message;

  /** Ocorrências agrupadas pelo firmware (ex.: Wi-Fi oscilando). */
  @Column(name = "occurrences", nullable = false)
  private int occurrences = 1;
}
