package com.n0hana.echoes_server.device;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.n0hana.echoes_server.animal.AnimalModel;
import com.n0hana.echoes_server.institution.InstitutionModel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Dispositivo físico autenticado no sistema.
 *
 * <p>
 * O dispositivo é identificado pelo hardware_id (hoje o MAC) e possui a chave pública usada para
 * validar os JWTs ES256 que ele mesmo emite. Nasce {@link DeviceStatus#PENDING}
 * e sem instituição; o admin o aprova e associa a uma instituição. O vínculo
 * com um animal é opcional.
 * </p>
 */
@Getter
@Setter
@Entity
@Table(name = "device_tb")
public class DeviceModel {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  private String name;

  @Column(name = "hardware_id", unique = true, nullable = false)
  private String hardwareId;

  @Column(name = "public_key", columnDefinition = "TEXT", nullable = false)
  private String publicKey;

  /** Sem default no banco: todo dispositivo novo grava o status explicitamente. */
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private DeviceStatus status = DeviceStatus.PENDING;

  /**
   * Instituição removida (soft delete) deixa de ser visível pelo
   * {@code @SQLRestriction}; IGNORE devolve null em vez de lançar, e a política
   * de acesso trata "sem instituição" como "não pode operar".
   */
  @NotFound(action = NotFoundAction.IGNORE)
  @ManyToOne(fetch = FetchType.LAZY, optional = true)
  @JoinColumn(name = "institution_id", nullable = true)
  private InstitutionModel institution;

  @ManyToOne(fetch = FetchType.LAZY, optional = true)
  @JoinColumn(name = "animal", nullable = true)
  @OnDelete(action = OnDeleteAction.SET_NULL)
  private AnimalModel animal;

  @Column(name = "approved_by")
  private UUID approvedBy;

  @Column(name = "approved_at")
  private LocalDateTime approvedAt;

  @Column(name = "last_seen_at")
  private LocalDateTime lastSeenAt;

  @CreationTimestamp
  @Column(name = "created_at", updatable = false)
  private LocalDateTime createdAt;

  public static DeviceModelBuilder builder() {
    return new DeviceModelBuilder();
  }

  public static class DeviceModelBuilder {
    private UUID id;
    private String name;
    private String hardwareId;
    private String publicKey;
    private DeviceStatus status;
    private InstitutionModel institution;
    private AnimalModel animal;

    public DeviceModelBuilder id(UUID id) {
      this.id = id;
      return this;
    }

    public DeviceModelBuilder name(String name) {
      this.name = name;
      return this;
    }

    public DeviceModelBuilder hardwareId(String hardwareId) {
      this.hardwareId = hardwareId;
      return this;
    }

    public DeviceModelBuilder publicKey(String publicKey) {
      this.publicKey = publicKey;
      return this;
    }

    public DeviceModelBuilder status(DeviceStatus status) {
      this.status = status;
      return this;
    }

    public DeviceModelBuilder institution(InstitutionModel institution) {
      this.institution = institution;
      return this;
    }

    public DeviceModelBuilder animal(AnimalModel animal) {
      this.animal = animal;
      return this;
    }

    public DeviceModel build() {
      DeviceModel device = new DeviceModel();
      device.setId(this.id);
      device.setName(this.name);
      device.setHardwareId(this.hardwareId);
      device.setPublicKey(this.publicKey);
      device.setStatus(this.status != null ? this.status : DeviceStatus.PENDING);
      device.setInstitution(this.institution);
      device.setAnimal(this.animal);
      return device;
    }
  }
}
