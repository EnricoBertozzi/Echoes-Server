package com.n0hana.echoes_server.device.event;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.n0hana.echoes_server.device.DeviceModel;
import com.n0hana.echoes_server.infra.security.SecurityConfig;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Recebe os eventos de uso enviados pelo ESP (alimentam a dashboard). */
@RestController
@RequestMapping("/api/v1/devices/me/events")
@SecurityRequirement(name = SecurityConfig.SECURITY)
@RequiredArgsConstructor
public class DeviceEventController {

  private final DeviceUsageService usageService;

  @PostMapping
  @PreAuthorize("hasRole('DEVICE')")
  public ResponseEntity<Void> report(@AuthenticationPrincipal DeviceModel device,
      @Valid @RequestBody DeviceEventRequest request) {
    usageService.report(device, request);
    return ResponseEntity.accepted().build();
  }
}
