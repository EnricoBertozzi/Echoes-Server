package com.n0hana.echoes_server.classroom;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.n0hana.echoes_server.device.DeviceDTO;
import com.n0hana.echoes_server.infra.security.SecurityConfig;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/classrooms/{id}/devices")
@SecurityRequirement(name = SecurityConfig.SECURITY)
@RequiredArgsConstructor
public class ClassroomDeviceController {

    private final ClassroomDeviceService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'TEACHER')")
    public ResponseEntity<List<DeviceDTO.DeviceInfo>> findDevices(@PathVariable UUID id, Principal principal) {
        return ResponseEntity.ok(service.findDevices(principal.getName(), id).stream()
                .map(DeviceDTO.DeviceInfo::from)
                .toList());
    }

    @PutMapping("/{deviceId}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<Void> addDevice(@PathVariable UUID id, @PathVariable UUID deviceId, Principal principal) {
        service.addDevice(principal.getName(), id, deviceId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{deviceId}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<Void> removeDevice(@PathVariable UUID id, @PathVariable UUID deviceId,
            Principal principal) {
        service.removeDevice(principal.getName(), id, deviceId);
        return ResponseEntity.noContent().build();
    }
}
