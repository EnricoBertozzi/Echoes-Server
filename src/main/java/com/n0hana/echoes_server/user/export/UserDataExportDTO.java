package com.n0hana.echoes_server.user.export;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.n0hana.echoes_server.term.model.DocumentType;
import com.n0hana.echoes_server.user.dto.UserDTO;

/**
 * Pacote de exportação dos dados do titular (RF18 / LGPD art. 18, II e V).
 *
 * <p>
 * Contém apenas dados vinculados ao próprio usuário: perfil, histórico de aceites
 * de termos, turmas em que participa, materiais que enviou e seus registros de
 * auditoria e os navegadores reconhecidos para o alerta de novo login (RF11).
 * Não inclui dados de outros usuários.
 * </p>
 */
public record UserDataExportDTO(
        Instant generatedAt,
        UserDTO profile,
        List<TermAcceptanceItem> termAcceptances,
        List<ClassroomItem> classrooms,
        List<MaterialItem> materials,
        List<ActivityItem> activityLog,
        List<KnownDeviceItem> knownDevices) {

    public record TermAcceptanceItem(
            DocumentType type,
            String version,
            Instant acceptedAt,
            Instant revokedAt,
            String ipAddress,
            String userAgent) {
    }

    public record ClassroomItem(
            UUID classroomId,
            String title,
            String period,
            LocalDateTime joinedAt) {
    }

    public record MaterialItem(
            UUID id,
            String title,
            String originalFileName,
            String contentType,
            long fileSize,
            LocalDateTime createdAt) {
    }

    public record ActivityItem(
            String action,
            String entity,
            String status,
            String ip,
            Instant timestamp) {
    }

    /**
     * Navegador reconhecido para o alerta de login em dispositivo novo (RF11). Só o
     * hash SHA-256 do User-Agent é guardado; {@code firstSeenAt} é nulo em registros
     * anteriores a 22/11/2026.
     */
    public record KnownDeviceItem(
            String userAgentSha256,
            Instant firstSeenAt,
            Instant expiresAt) {
    }
}
