package com.n0hana.echoes_server.device;

import org.javers.core.metamodel.annotation.ShallowReference;
import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.UpdateTimestamp;

import com.n0hana.echoes_server.auscultation.AuscultationPointModel;
import com.n0hana.echoes_server.scenario.ScenarioModel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Cenário ativo de um ponto de ausculta em um dispositivo: é o áudio que o ESP
 * toca naquele ponto. Cada dispositivo tem no máximo um cenário por ponto.
 *
 * <p>
 * Excluir o dispositivo, o ponto ou o cenário apaga a escolha (ON DELETE
 * CASCADE), então nenhum deles fica preso por causa dela.
 * </p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "device_scenario_tb", uniqueConstraints = @UniqueConstraint(columnNames = { "device_id", "point_id" }))
public class DeviceScenarioModel {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ShallowReference
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @OnDelete(action = OnDeleteAction.CASCADE)
  @JoinColumn(name = "device_id", nullable = false)
  private DeviceModel device;

  @ShallowReference
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @OnDelete(action = OnDeleteAction.CASCADE)
  @JoinColumn(name = "point_id", nullable = false)
  private AuscultationPointModel point;

  @ShallowReference
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @OnDelete(action = OnDeleteAction.CASCADE)
  @JoinColumn(name = "scenario_id", nullable = false)
  private ScenarioModel scenario;

  @UpdateTimestamp
  @Column(name = "updated_at")
  private LocalDateTime updatedAt;
}
