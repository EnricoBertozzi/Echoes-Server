package com.n0hana.echoes_server.infra.logs;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.n0hana.echoes_server.infra.security.SecurityConfig;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;

/**
 * Endpoint somente leitura para consulta dos registros de auditoria.
 *
 * <p>Restrito a {@code ADMIN} porque os logs contêm IP, ID de usuário e
 * detalhes de operações sensíveis. A escrita é feita exclusivamente pelo
 * {@link AuditAspect} — este controller não expõe POST/PUT/DELETE, o que
 * alinha com a imutabilidade de {@link AuditLog}.</p>
 *
 * @author Enrico Bertozzi
 * @since 0.1.2
 */
@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
@SecurityRequirement(name = SecurityConfig.SECURITY)
public class AuditLogController {

    private final AuditLogService service;

    /**
     * Lista os registros de auditoria de forma paginada.
     *
     * <p>Aceita os parâmetros padrão do Spring Data ({@code page}, {@code size},
     * {@code sort}). Exemplo: {@code ?page=0&size=20&sort=timestamp,desc}.</p>
     *
     * @param pageable Configuração de paginação e ordenação.
     * @return {@link ResponseEntity} com página de {@link AuditLogDTO} e HTTP 200.
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<AuditLogDTO>> list(Pageable pageable) {
        Page<AuditLogDTO> page = service.getLogs(pageable).map(AuditLogDTO::from);
        return ResponseEntity.ok(page);
    }
}
