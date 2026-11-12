package com.n0hana.echoes_server.classroom;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ClassroomDTO {

    // usado para criar e para alterar a turma
    public record Request(
            @NotBlank(message = "O título é obrigatório") @Size(max = 100, message = "O título deve ter no máximo 100 caracteres") String title,
            @Size(max = 500, message = "A descrição deve ter no máximo 500 caracteres") String description) {
    }

    public record Info(
            UUID id,
            String title,
            String description,
            boolean archived,
            UUID institutionId,
            LocalDateTime createdAt) {

        public static Info from(ClassroomModel model) {
            return new Info(
                    model.getId(),
                    model.getTitle(),
                    model.getDescription(),
                    model.isArchived(),
                    model.getInstitution().getId(),
                    model.getCreatedAt());
        }
    }
}
