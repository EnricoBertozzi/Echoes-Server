package com.n0hana.echoes_server.device;

import java.util.UUID;

/**
 * Resultado do autocadastro de um dispositivo via MQTT.
 *
 * @param status   resultado da operação
 * @param mac      MAC informado pelo dispositivo
 * @param deviceId id do dispositivo quando já existente ou recém-criado
 * @param message  mensagem legível para o firmware
 */
public record DeviceRegistrationResult(
    Status status,
    String mac,
    UUID deviceId,
    String message) {

  public enum Status {
    REGISTERED,
    ALREADY_REGISTERED,
    REJECTED
  }

  public static DeviceRegistrationResult registered(DeviceModel device) {
    return new DeviceRegistrationResult(
        Status.REGISTERED,
        device.getMac(),
        device.getId(),
        "Dispositivo registrado");
  }

  public static DeviceRegistrationResult alreadyRegistered(DeviceModel device) {
    return new DeviceRegistrationResult(
        Status.ALREADY_REGISTERED,
        device.getMac(),
        device.getId(),
        "Dispositivo já registrado com esta chave pública");
  }

  public static DeviceRegistrationResult rejected(String mac, String message) {
    return new DeviceRegistrationResult(Status.REJECTED, mac, null, message);
  }
}
