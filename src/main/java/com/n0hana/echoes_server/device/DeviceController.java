package com.n0hana.echoes_server.device;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.n0hana.echoes_server.device.DeviceDTO.DeviceInfo;
import com.n0hana.echoes_server.infra.security.SecurityConfig;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;

/**
 * Controller de leitura de dispositivos.
 *
 * <p>
 * O cadastro, a atualização e a exclusão são feitos diretamente no banco de
 * dados, por isso não existem endpoints de escrita para esta entidade.
 * </p>
 */
@RestController
@RequestMapping("/devices")
@SecurityRequirement(name = SecurityConfig.SECURITY)
public class DeviceController {

  @Autowired
  private DeviceService deviceService;

  /**
   * Retorna os dados do dispositivo autenticado pelo próprio token.
   */
  @GetMapping("/me")
  @PreAuthorize("hasRole('DEVICE')")
  public ResponseEntity<DeviceInfo> findMyDevice(@AuthenticationPrincipal DeviceModel device) {
    if (device == null)
      throw new AccessDeniedException("Dispositivo não autenticado");

    return ResponseEntity.ok(DeviceInfo.from(device));
  }

  /**
   * Lista os dispositivos cadastrados.
   */
  @GetMapping
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<List<DeviceInfo>> findAllDevices(@RequestParam int page, @RequestParam int size) {
    List<DeviceInfo> list = deviceService
        .findAllDevices(page, size)
        .stream()
        .map(DeviceInfo::from)
        .toList();

    return ResponseEntity.ok(list);
  }

  /**
   * Busca dispositivos pelo nome.
   */
  @GetMapping("/search")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<List<DeviceInfo>> findDevicesByName(@RequestParam String name, @RequestParam int page,
      @RequestParam int size) {
    List<DeviceInfo> list = deviceService
        .findDevicesByName(name, page, size)
        .stream()
        .map(DeviceInfo::from)
        .toList();

    return ResponseEntity.ok(list);
  }

  /**
   * Busca um dispositivo pelo id.
   */
  @GetMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<DeviceInfo> findDeviceById(@PathVariable("id") UUID id) {
    return ResponseEntity.ok(DeviceInfo.from(deviceService.findDeviceById(id)));
  }
}
