package com.n0hana.echoes_server.device;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.n0hana.echoes_server.device.DeviceDTO.DeviceInfo;
import com.n0hana.echoes_server.device.DeviceDTO.DeviceInstitutionRequest;
import com.n0hana.echoes_server.device.DeviceDTO.UpdateDeviceRequest;
import com.n0hana.echoes_server.infra.security.SecurityConfig;
import com.n0hana.echoes_server.user.model.User;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Controller de dispositivos.
 *
 * <p>
 * O cadastro não passa por aqui: o ESP se autocadastra por MQTT e fica PENDING.
 * Aprovar, mover, ativar, inativar e excluir são exclusivos de ADMIN.
 * </p>
 */
@RestController
@RequestMapping("/devices")
@SecurityRequirement(name = SecurityConfig.SECURITY)
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    private final DeviceLifecycleService lifecycleService;

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

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Page<DeviceInfo>> findAllDevices(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) DeviceStatus status,
            @RequestParam(required = false) UUID institutionId,
            Pageable pageable) {
        Page<DeviceInfo> devices = deviceService
                .findDevices(user, name, status, institutionId, pageable)
                .map(DeviceInfo::from);

        return ResponseEntity.ok(devices);
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
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<DeviceInfo> findDeviceById(@PathVariable("id") UUID id,
            @AuthenticationPrincipal User principal) {
        return ResponseEntity.ok(DeviceInfo.from(deviceService.findDeviceById(id, principal)));
    }

    /**
     * Aprova um dispositivo pendente e o associa a uma instituição.
     */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DeviceInfo> approve(@PathVariable("id") UUID id,
            @Valid @RequestBody DeviceInstitutionRequest request,
            @AuthenticationPrincipal User admin) {
        return ResponseEntity.ok(DeviceInfo.from(
                lifecycleService.approve(id, request.institutionId(), admin.getId())));
    }

    /**
     * Atualiza parcialmente um dispositivo: nome, instituição e/ou animal. Campos
     * omitidos permanecem como estão.
     */
    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DeviceInfo> update(@PathVariable("id") UUID id,
            @Valid @RequestBody UpdateDeviceRequest request) {
        return ResponseEntity.ok(DeviceInfo.from(lifecycleService.update(id, request)));
    }

    /**
     * Move um dispositivo já aprovado para outra instituição.
     */
    @PatchMapping("/{id}/institution")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DeviceInfo> changeInstitution(@PathVariable("id") UUID id,
            @Valid @RequestBody DeviceInstitutionRequest request) {
        return ResponseEntity.ok(DeviceInfo.from(
                lifecycleService.changeInstitution(id, request.institutionId())));
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DeviceInfo> activate(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(DeviceInfo.from(lifecycleService.activate(id)));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DeviceInfo> deactivate(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(DeviceInfo.from(lifecycleService.deactivate(id)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id) {
        lifecycleService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
