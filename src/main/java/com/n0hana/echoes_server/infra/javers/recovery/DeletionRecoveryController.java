package com.n0hana.echoes_server.infra.javers.recovery;

import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.n0hana.echoes_server.infra.javers.recovery.DeletionRecovery.Status;
import com.n0hana.echoes_server.infra.javers.recovery.DeletionRecovery.Type;
import com.n0hana.echoes_server.infra.javers.recovery.DeletionRecoveryService.Detail;
import com.n0hana.echoes_server.infra.javers.recovery.DeletionRecoveryService.Result;
import com.n0hana.echoes_server.infra.javers.recovery.DeletionRecoveryTargets.Summary;
import com.n0hana.echoes_server.infra.logs.Auditable;
import com.n0hana.echoes_server.infra.web.PageLimits;
import com.n0hana.echoes_server.user.model.User;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/restorations")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class DeletionRecoveryController {

    private final DeletionRecoveryService service;

    @GetMapping
    public Page<Summary> list(@RequestParam(required = false) Type type,
            @RequestParam(required = false) Status status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.list(type, status, PageRequest.of(PageLimits.clampPage(page), PageLimits.clamp(size)));
    }

    @GetMapping("/{type}/{id}")
    public Detail detail(@PathVariable Type type, @PathVariable UUID id, @AuthenticationPrincipal User admin) {
        return service.detail(type, id, admin.getId());
    }

    @PostMapping("/{type}/{id}/request")
    @Auditable(action = "DELETION_RESTORE_REQUESTED", entity = "DeletionRecovery", entityId = "#id")
    public ResponseEntity<Void> request(@PathVariable Type type, @PathVariable UUID id, @AuthenticationPrincipal User admin) {
        service.request(type, id, admin.getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{type}/{id}/approve")
    @Auditable(action = "DELETION_RESTORE_APPROVED", entity = "DeletionRecovery", entityId = "#id")
    public ResponseEntity<Void> approve(@PathVariable Type type, @PathVariable UUID id, @AuthenticationPrincipal User admin) {
        service.approve(type, id, admin.getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{type}/{id}/execute")
    @Auditable(action = "DELETION_RESTORED", entity = "DeletionRecovery", entityId = "#id",
            details = "'Tipo: ' + #type + '; vínculos recuperados: ' + #result.recoveredMembers()"
                    + " + '; vínculos ignorados: ' + #result.skippedMembers().size()")
    public Result execute(@PathVariable Type type, @PathVariable UUID id) {
        return service.execute(type, id);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> recoveryError(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of("message", exception.getReason()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> concurrentConflict() {
        return ResponseEntity.status(409).body(Map.of("message",
                "Um cadastro ou vínculo mudou durante a restauração. Atualize o caso e tente novamente."));
    }
}
