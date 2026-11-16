package com.n0hana.echoes_server.device;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.n0hana.echoes_server.device.DeviceScenarioDTO.DeviceConfig;
import com.n0hana.echoes_server.device.DeviceScenarioDTO.SelectScenarioRequest;
import com.n0hana.echoes_server.infra.security.SecurityConfig;
import com.n0hana.echoes_server.user.model.User;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Cenário atual de cada ponto de ausculta de um dispositivo: o ESP lê para
 * sincronizar e professor, gestor e admin escolhem.
 */
@RestController
@RequestMapping("/api/v1/devices")
@SecurityRequirement(name = SecurityConfig.SECURITY)
@RequiredArgsConstructor
public class DeviceScenarioController {

  private final DeviceScenarioService service;

  /**
   * Rota de sincronização do ESP: pontos do animal com o cenário atual e a URI
   * do áudio de cada um.
   */
  @GetMapping("/me/scenario")
  @PreAuthorize("hasRole('DEVICE')")
  public ResponseEntity<DeviceConfig> findMyConfig(@AuthenticationPrincipal DeviceModel device) {
    if (device == null)
      throw new AccessDeniedException("Dispositivo não autenticado");
    return ResponseEntity.ok(service.findConfig(device));
  }

  /** Configuração de um dispositivo para o painel. */
  @GetMapping("/{deviceId}/scenario")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'TEACHER')")
  public ResponseEntity<DeviceConfig> findConfig(@PathVariable("deviceId") UUID deviceId,
      @AuthenticationPrincipal User user) {
    return ResponseEntity.ok(service.findConfig(user, deviceId));
  }

  /** Escolhe o cenário atual de um ponto do dispositivo. */
  @PutMapping("/{deviceId}/points/{pointId}/scenario")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'TEACHER')")
  public ResponseEntity<Void> select(@PathVariable("deviceId") UUID deviceId,
      @PathVariable("pointId") UUID pointId,
      @RequestBody @Valid SelectScenarioRequest dto,
      @AuthenticationPrincipal User user) {
    service.select(user, deviceId, pointId, dto.scenarioId());
    return ResponseEntity.noContent().build();
  }
}
