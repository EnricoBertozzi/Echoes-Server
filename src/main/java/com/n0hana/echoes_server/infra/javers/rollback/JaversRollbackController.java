package com.n0hana.echoes_server.infra.javers.rollback;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.javers.core.Javers;
import org.javers.repository.jql.QueryBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.n0hana.echoes_server.device.DeviceJwtService;
import com.n0hana.echoes_server.infra.javers.checkpoint.JaversCheckpointService;
import com.n0hana.echoes_server.infra.javers.retention.JaversRetentionService;
import com.n0hana.echoes_server.infra.logs.Auditable;
import com.n0hana.echoes_server.user.model.User;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class JaversRollbackController {

    private final Javers javers;
    private final JaversRollbackService rollback;
    private final JaversCheckpointService checkpoints;
    private final JaversRetentionService retention;
    private final DeviceJwtService deviceJwt;

    @GetMapping("/javers/history/{entityType}/{id}")
    @Auditable(action = "JAVERS_HISTORY_VIEWED", entity = "Javers", entityId = "#id")
    public ResponseEntity<List<JaversCommitDTO>> history(@PathVariable String entityType, @PathVariable UUID id) {
        Class<?> concrete = rollback.concreteClass(rollback.resolveType(entityType), id);
        List<JaversCommitDTO> history = javers
                .findSnapshots(QueryBuilder.byInstanceId(id, concrete).limit(100).build())
                .stream().map(JaversCommitDTO::from).toList();
        return ResponseEntity.ok(history);
    }

    @PostMapping("/javers/rollback")
    @Auditable(action = "JAVERS_ROLLBACK_REQUESTED", entity = "Javers", entityId = "#dto.entityId()")
    public ResponseEntity<RollbackResult> rollback(@RequestBody @Valid JaversRollbackDTO dto) {
        return ResponseEntity.ok(rollback.rollbackEntity(
                dto.checkpointId(), rollback.resolveType(dto.entityType()), dto.entityId()));
    }

    @PostMapping("/javers/rollback/checkpoint/{id}")
    @Auditable(action = "JAVERS_CHECKPOINT_ROLLBACK", entity = "Javers", entityId = "#id")
    public ResponseEntity<List<RollbackResult>> rollbackCheckpoint(@PathVariable UUID id) {
        return ResponseEntity.ok(rollback.rollbackCheckpoint(id));
    }

    @PostMapping("/javers/checkpoint")
    @Auditable(action = "JAVERS_CHECKPOINT_CREATED", entity = "Javers")
    public ResponseEntity<JaversCheckpointDTO> createCheckpoint(@RequestBody @Valid JaversCheckpointRequestDTO dto,
            @AuthenticationPrincipal User requester) {
        var checkpoint = checkpoints.createForIds(
                rollback.resolveType(dto.entityType()), dto.entityIds(), requester.getId().toString());
        return ResponseEntity.status(HttpStatus.CREATED).body(JaversCheckpointDTO.from(checkpoint));
    }

    @PostMapping("/javers/checkpoint/{id}/approve")
    @Auditable(action = "JAVERS_CHECKPOINT_APPROVED", entity = "Javers", entityId = "#id")
    public ResponseEntity<Void> approve(@PathVariable UUID id, @RequestBody(required = false) JaversApproveDTO dto,
            @AuthenticationPrincipal User approver) {
        String approvedBy = approver.getId().toString();
        if (dto != null && dto.approvedBy() != null && !dto.approvedBy().equals(approvedBy))
            throw new IllegalStateException("approvedBy precisa ser o usuário autenticado");

        checkpoints.approve(id, approvedBy);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/javers/prune/run")
    @Auditable(action = "JAVERS_PRUNED_MANUAL", entity = "Javers")
    public ResponseEntity<Map<String, Integer>> prune() {
        return ResponseEntity.ok(Map.of("removed", retention.pruneAll()));
    }

    /** Mitigação manual: invalida os tokens já emitidos para o dispositivo (ex.: depois de um rollback de User). */
    @PostMapping("/devices/{id}/revoke-tokens")
    @Auditable(action = "DEVICE_TOKENS_REVOKED", entity = "Device", entityId = "#id")
    public ResponseEntity<Void> revokeTokens(@PathVariable UUID id) {
        deviceJwt.revokeAllForDevice(id);
        return ResponseEntity.noContent().build();
    }
}
