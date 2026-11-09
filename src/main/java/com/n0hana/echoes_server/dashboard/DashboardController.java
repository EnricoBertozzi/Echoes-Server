package com.n0hana.echoes_server.dashboard;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.n0hana.echoes_server.dashboard.DashboardDTO.DevicesSummary;
import com.n0hana.echoes_server.dashboard.DashboardDTO.Rankings;
import com.n0hana.echoes_server.dashboard.DashboardDTO.UsageSummary;
import com.n0hana.echoes_server.infra.security.SecurityConfig;
import com.n0hana.echoes_server.user.model.User;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;

/**
 * Dashboard. ADMIN (global, com filtro opcional por instituição) e MANAGER
 * (sempre a própria instituição). Professor e estudante não têm dashboard.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@SecurityRequirement(name = SecurityConfig.SECURITY)
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
@RequiredArgsConstructor
public class DashboardController {

  private final DashboardService dashboardService;

  @GetMapping("/devices")
  public ResponseEntity<DevicesSummary> devices(@AuthenticationPrincipal User user,
      @RequestParam(required = false) UUID institutionId) {
    return ResponseEntity.ok(dashboardService.devices(user, institutionId));
  }

  @GetMapping("/usage")
  public ResponseEntity<UsageSummary> usage(@AuthenticationPrincipal User user,
      @RequestParam(required = false) UUID institutionId,
      @RequestParam(defaultValue = "30") int days) {
    return ResponseEntity.ok(dashboardService.usage(user, institutionId, days));
  }

  @GetMapping("/rankings")
  public ResponseEntity<Rankings> rankings(@AuthenticationPrincipal User user,
      @RequestParam(required = false) UUID institutionId,
      @RequestParam(defaultValue = "30") int days) {
    return ResponseEntity.ok(dashboardService.rankings(user, institutionId, days));
  }
}
