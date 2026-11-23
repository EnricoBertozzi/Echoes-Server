package com.n0hana.echoes_server.device.log;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.n0hana.echoes_server.device.DeviceModel;
import com.n0hana.echoes_server.infra.security.SecurityConfig;
import com.n0hana.echoes_server.user.model.User;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Ingestão (ESP) e consulta (admin, gestor e professor) dos logs de dispositivo. */
@RestController
@RequestMapping("/api/v1/devices")
@SecurityRequirement(name = SecurityConfig.SECURITY)
@RequiredArgsConstructor
public class DeviceLogController {

  private final DeviceLogIngestService ingestService;
  private final DeviceLogQueryService queryService;

  @PostMapping("/me/logs")
  @PreAuthorize("hasRole('DEVICE')")
  public ResponseEntity<Void> ingest(@AuthenticationPrincipal DeviceModel device,
      @Valid @RequestBody DeviceLogRequest request) {
    ingestService.ingest(device, request);
    return ResponseEntity.accepted().build();
  }

  /**
   * Logs do dispositivo, do mais recente ao mais antigo. {@code minLevel} traz
   * aquele nível e os mais graves (ex.: WARN = WARN e ERROR).
   */
  @GetMapping("/{id}/logs")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'TEACHER')")
  public ResponseEntity<Page<DeviceLogResponse>> list(@PathVariable("id") UUID id,
      @AuthenticationPrincipal User user,
      @RequestParam(required = false) DeviceLogLevel minLevel,
      @RequestParam(required = false) DeviceLogCategory category,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    return ResponseEntity.ok(queryService.find(user, id, minLevel, category, from, to, page, size));
  }
}
