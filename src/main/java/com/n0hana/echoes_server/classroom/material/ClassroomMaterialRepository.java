package com.n0hana.echoes_server.classroom.material;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClassroomMaterialRepository extends JpaRepository<ClassroomMaterial, UUID> {
    @EntityGraph(attributePaths = {"author", "classroom", "classroom.institution"})
    List<ClassroomMaterial> findByClassroomIdOrderByCreatedAtDesc(UUID classroomId);
    @EntityGraph(attributePaths = {"author", "classroom", "classroom.institution"})
    Optional<ClassroomMaterial> findByIdAndClassroomId(UUID id, UUID classroomId);

    @EntityGraph(attributePaths = {"classroom"})
    List<ClassroomMaterial> findByAuthorId(UUID authorId);

    @Query("select m.storedFileName from ClassroomMaterial m where m.classroom.id = :classroomId")
    List<String> findStoredNamesByClassroomId(@Param("classroomId") UUID classroomId);

    @Modifying
    @Query("delete from ClassroomMaterial m where m.classroom.id = :classroomId")
    int deleteAllByClassroomId(@Param("classroomId") UUID classroomId);
}
