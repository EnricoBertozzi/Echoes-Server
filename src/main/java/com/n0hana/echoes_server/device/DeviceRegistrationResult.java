package com.n0hana.echoes_server.device;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Resultado do autocadastro de um dispositivo via MQTT.
 *
 * @param status       resultado da operação
 * @param hardwareId          identificador informado pelo dispositivo
 * @param deviceId     id do dispositivo quando já existente ou recém-criado
 * @param deviceStatus estado do dispositivo (PENDING até o admin aprovar);
 *                     nulo quando o pedido foi rejeitado
 * @param message      mensagem legível para o firmware
 */
public record DeviceRegistrationResult(
    Status status,
    @JsonProperty("hardware_id") String hardwareId,
    UUID deviceId,
    DeviceStatus deviceStatus,
    String message) {

  public enum Status {
    REGISTERED,
    ALREADY_REGISTERED,
    REJECTED
  }

  public static DeviceRegistrationResult registered(DeviceModel device) {
    return new DeviceRegistrationResult(
        Status.REGISTERED,
        device.getHardwareId(),
        device.getId(),
        device.getStatus(),
        "Dispositivo registrado, aguardando aprovação");
  }

  public static DeviceRegistrationResult alreadyRegistered(DeviceModel device) {
    return new DeviceRegistrationResult(
        Status.ALREADY_REGISTERED,
        device.getHardwareId(),
        device.getId(),
        device.getStatus(),
        "Dispositivo já registrado com esta chave pública");
  }

  public static DeviceRegistrationResult rejected(String hardwareId, String message) {
    return new DeviceRegistrationResult(Status.REJECTED, hardwareId, null, null, message);
  }
}
