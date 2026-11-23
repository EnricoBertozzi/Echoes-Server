package com.n0hana.echoes_server.classroom;

import java.time.LocalDateTime;
import java.util.UUID;

import com.n0hana.echoes_server.user.model.User;
import com.n0hana.echoes_server.user.model.UserRole;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ClassroomDTO {

    // usado para criar e para alterar a turma, o período é ano e semestre
    public record Request(
            @NotBlank(message = "O título é obrigatório") @Size(max = 100, message = "O título deve ter no máximo 100 caracteres") String title,
            @Size(max = 500, message = "A descrição deve ter no máximo 500 caracteres") String description,
            @NotBlank(message = "O período é obrigatório") @Pattern(regexp = "\\d{4}/0[12]", message = "O período deve seguir o formato AAAA/SS, por exemplo 2026/01") String period) {
    }

    // aceita maiúsculo ou minúsculo, sem I, O, 0 e 1
    public record JoinRequest(
            @NotBlank(message = "O código é obrigatório") @Pattern(regexp = "(?i)[A-HJ-NP-Z2-9]{6}", message = "Código inválido") String code) {
    }

    public record CodeResponse(String code, long expiresInSeconds) {
    }

    // joinCode só vem preenchido na consulta por id e só para professor da turma
    public record Info(
            UUID id,
            String title,
            String description,
            String period,
            boolean archived,
            UUID institutionId,
            LocalDateTime createdAt,
            String joinCode) {

        public static Info from(ClassroomModel model) {
            return from(model, null);
        }

        public static Info from(ClassroomModel model, String joinCode) {
            return new Info(
                    model.getId(),
                    model.getTitle(),
                    model.getDescription(),
                    model.getPeriod(),
                    model.isArchived(),
                    model.getInstitution().getId(),
                    model.getCreatedAt(),
                    joinCode);
        }
    }

    public record MemberInfo(
            UUID userId,
            String name,
            String email,
            UserRole role,
            LocalDateTime joinedAt) {

        public static MemberInfo from(ClassroomMember member) {
            User user = member.getUser();
            return new MemberInfo(
                    user.getId(),
                    user.getName(),
                    user.getEmail(),
                    user.getUserRole(),
                    member.getJoinedAt());
        }

        public MemberInfo withoutEmail() {
            return new MemberInfo(userId, name, null, role, joinedAt);
        }
    }
}
