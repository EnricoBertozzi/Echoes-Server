package com.n0hana.echoes_server.user.export;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.n0hana.echoes_server.auth.device.KnownDeviceService;
import com.n0hana.echoes_server.classroom.ClassroomMember;
import com.n0hana.echoes_server.classroom.ClassroomMemberRepository;
import com.n0hana.echoes_server.classroom.material.ClassroomMaterial;
import com.n0hana.echoes_server.classroom.material.ClassroomMaterialRepository;
import com.n0hana.echoes_server.infra.logs.AuditLog;
import com.n0hana.echoes_server.infra.logs.AuditLogRepository;
import com.n0hana.echoes_server.infra.logs.Auditable;
import com.n0hana.echoes_server.term.TermService;
import com.n0hana.echoes_server.term.model.UserTermAcceptance;
import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.exception.UserNotFoundException;
import com.n0hana.echoes_server.user.model.User;
import com.n0hana.echoes_server.user.service.UserInfoService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserDataExportService {

    private final UserRepository userRepository;
    private final UserInfoService userInfoService;
    private final TermService termService;
    private final ClassroomMemberRepository memberRepository;
    private final ClassroomMaterialRepository materialRepository;
    private final AuditLogRepository auditLogRepository;
    private final KnownDeviceService knownDeviceService;

    @Transactional(readOnly = true)
    @Auditable(action = "EXPORT_DATA", entity = "User")
    public UserDataExportDTO export(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(UserNotFoundException::new);
        String userId = user.getId().toString();

        return new UserDataExportDTO(
                Instant.now(),
                userInfoService.findUserByEmail(email),
                termItems(user),
                classroomItems(user),
                materialItems(user),
                activityItems(userId),
                knownDeviceItems(user));
    }

    private List<UserDataExportDTO.TermAcceptanceItem> termItems(User user) {
        return termService.getUserAcceptances(user.getId()).stream()
                .map(this::toTermItem)
                .toList();
    }

    private UserDataExportDTO.TermAcceptanceItem toTermItem(UserTermAcceptance acceptance) {
        return new UserDataExportDTO.TermAcceptanceItem(
                acceptance.getTerm().getType(),
                acceptance.getTerm().getVersion(),
                acceptance.getAcceptedAt(),
                acceptance.getRevokedAt(),
                acceptance.getIpAddress(),
                acceptance.getUserAgent());
    }

    private List<UserDataExportDTO.ClassroomItem> classroomItems(User user) {
        return memberRepository.findByUserId(user.getId()).stream()
                .map(this::toClassroomItem)
                .toList();
    }

    private UserDataExportDTO.ClassroomItem toClassroomItem(ClassroomMember member) {
        return new UserDataExportDTO.ClassroomItem(
                member.getClassroom().getId(),
                member.getClassroom().getTitle(),
                member.getClassroom().getPeriod(),
                member.getJoinedAt());
    }

    private List<UserDataExportDTO.MaterialItem> materialItems(User user) {
        return materialRepository.findByAuthorId(user.getId()).stream()
                .map(this::toMaterialItem)
                .toList();
    }

    private UserDataExportDTO.MaterialItem toMaterialItem(ClassroomMaterial material) {
        return new UserDataExportDTO.MaterialItem(
                material.getId(),
                material.getTitle(),
                material.getOriginalFileName(),
                material.getContentType(),
                material.getFileSize(),
                material.getCreatedAt());
    }

    private List<UserDataExportDTO.ActivityItem> activityItems(String userId) {
        return auditLogRepository.findByUserIdOrderByTimestampDesc(userId).stream()
                .map(this::toActivityItem)
                .toList();
    }

    private List<UserDataExportDTO.KnownDeviceItem> knownDeviceItems(User user) {
        return knownDeviceService.list(user.getId()).stream()
                .map(device -> new UserDataExportDTO.KnownDeviceItem(
                        device.fingerprint(),
                        device.firstSeenAt(),
                        device.expiresAt()))
                .toList();
    }

    private UserDataExportDTO.ActivityItem toActivityItem(AuditLog log) {
        return new UserDataExportDTO.ActivityItem(
                log.getAction(),
                log.getEntity(),
                log.getStatus(),
                log.getIp(),
                log.getTimestamp());
    }
}
