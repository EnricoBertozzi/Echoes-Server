package com.n0hana.echoes_server.device;

import java.util.UUID;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.n0hana.echoes_server.animal.AnimalModel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * O dispositivo é identificado pelo MAC e possui a chave pública usada para
 * validar os JWTs ES256 que ele mesmo emite. O vínculo com um animal é
 * opcional: um dispositivo pode existir cadastrado sem estar associado a um
 * animal.
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

  @Column(name = "mac", unique = true, nullable = false)
  private String mac;

  @Column(name = "public_key", columnDefinition = "TEXT", nullable = false)
  private String publicKey;

  @ManyToOne(fetch = FetchType.LAZY, optional = true)
  @JoinColumn(name = "animal", nullable = true)
  @OnDelete(action = OnDeleteAction.SET_NULL)
  private AnimalModel animal;

  public static DeviceModelBuilder builder() {
    return new DeviceModelBuilder();
  }

  public static class DeviceModelBuilder {
    private UUID id;
    private String name;
    private String mac;
    private String publicKey;
    private AnimalModel animal;

    public DeviceModelBuilder id(UUID id) {
      this.id = id;
      return this;
    }

    public DeviceModelBuilder name(String name) {
      this.name = name;
      return this;
    }

    public DeviceModelBuilder mac(String mac) {
      this.mac = mac;
      return this;
    }

    public DeviceModelBuilder publicKey(String publicKey) {
      this.publicKey = publicKey;
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
      device.setMac(this.mac);
      device.setPublicKey(this.publicKey);
      device.setAnimal(this.animal);
      return device;
    }
  }
}
