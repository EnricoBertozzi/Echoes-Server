package com.n0hana.echoes_server.classroom;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClassroomMaterialRepository extends JpaRepository<ClassroomMaterial, UUID> {

    // busca por título e tópico, texto vazio traz tudo
    Page<ClassroomMaterial> findByClassroomIdAndTitleContainingIgnoreCaseAndTopicContainingIgnoreCase(
            UUID classroomId, String title, String topic, Pageable pageable);

    // garante que o material pertence à turma da rota
    Optional<ClassroomMaterial> findByIdAndClassroomId(UUID id, UUID classroomId);
}
