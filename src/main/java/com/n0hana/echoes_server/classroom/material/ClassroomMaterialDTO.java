package com.n0hana.echoes_server.classroom.material;

import java.time.LocalDateTime;
import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class ClassroomMaterialDTO {
    private ClassroomMaterialDTO() {}

    public record Update(
            @NotBlank @Size(max = 150) String title,
            @Size(max = 1000) String description) {}

    public record Info(UUID id, String title, String description, String originalFileName,
                       String contentType, long fileSize, UUID authorId, String authorName,
                       LocalDateTime createdAt, LocalDateTime updatedAt, boolean previewable) {
        public static Info from(ClassroomMaterial model) {
            return new Info(model.getId(), model.getTitle(), model.getDescription(),
                    model.getOriginalFileName(), model.getContentType(), model.getFileSize(),
                    model.getAuthor().getId(), model.getAuthor().getName(),
                    model.getCreatedAt(), model.getUpdatedAt(),
                    MaterialFileType.fromFilename(model.getOriginalFileName()).previewable());
        }
    }
}
