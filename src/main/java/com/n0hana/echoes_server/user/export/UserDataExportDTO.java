package com.n0hana.echoes_server.user.export;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.n0hana.echoes_server.term.model.DocumentType;
import com.n0hana.echoes_server.user.dto.UserDTO;

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

    public record KnownDeviceItem(
            String userAgentSha256,
            Instant firstSeenAt,
            Instant expiresAt) {
    }
}
