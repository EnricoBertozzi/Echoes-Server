
package com.n0hana.echoes_server.infra.logs;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;

import org.springframework.http.ResponseEntity;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.n0hana.echoes_server.infra.security.SecurityConfig;
import com.n0hana.echoes_server.user.model.User;
import com.n0hana.echoes_server.user.model.UserRole;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;

/**
 * Endpoint de consulta dos registros de auditoria.
 *
 * ADMIN:
 * - Consulta todos os registros.
 * - Pode filtrar qualquer instituição.
 *
 * MANAGER:
 * - Consulta exclusivamente sua instituição.
 *
 * O campo {@code ip} só é retornado para ADMIN; para MANAGER vem nulo.
 *
 * A escrita permanece exclusiva do AuditAspect.
 */
@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
@SecurityRequirement(name = SecurityConfig.SECURITY)
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
public class AuditLogController {

    private final AuditLogService service;

    /**
     * GET /api/v1/audit-logs
     *
     * Parâmetros:
     * - page
     * - size
     * - sort
     * - institutionId
     * - entity
     * - action
     * - status
     */
    @GetMapping
    public ResponseEntity<Page<AuditLogDTO>> list(
            @AuthenticationPrincipal User user,

            @RequestParam(required = false)
            UUID institutionId,

            @RequestParam(required = false)
            String entity,

            @RequestParam(required = false)
            String action,

            @RequestParam(required = false)
            String status,

            @PageableDefault(
                size = 20,
                sort = "timestamp",
                direction = Sort.Direction.DESC
            )
            Pageable pageable
    ) {
        Page<AuditLogDTO> page = service
                .getLogs(
                    user,
                    institutionId,
                    entity,
                    action,
                    status,
                    pageable
                )
                .map(log -> AuditLogDTO.from(
                    log,
                    user.getUserRole() == UserRole.ADMIN
                ));

        return ResponseEntity.ok(page);
    }
}

