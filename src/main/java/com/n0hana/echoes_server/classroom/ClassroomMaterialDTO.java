package com.n0hana.echoes_server.classroom;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ClassroomMaterialDTO {

    // usado para postar e para editar, o arquivo vai separado
    public record Request(
            @NotBlank(message = "O título é obrigatório") @Size(max = 100, message = "O título deve ter no máximo 100 caracteres") String title,
            @NotBlank(message = "O tópico é obrigatório") @Size(max = 100, message = "O tópico deve ter no máximo 100 caracteres") String topic) {
    }

    public record Info(
            UUID id,
            UUID classroomId,
            String title,
            String topic,
            UUID authorId,
            String authorName,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {

        public static Info from(ClassroomMaterial material) {
            return new Info(
                    material.getId(),
                    material.getClassroom().getId(),
                    material.getTitle(),
                    material.getTopic(),
                    material.getAuthor().getId(),
                    material.getAuthor().getName(),
                    material.getCreatedAt(),
                    material.getUpdatedAt());
        }
    }

    // caminho do arquivo no disco e o nome que o usuário recebe ao baixar
    public record Download(Path path, String name) {
    }
}
