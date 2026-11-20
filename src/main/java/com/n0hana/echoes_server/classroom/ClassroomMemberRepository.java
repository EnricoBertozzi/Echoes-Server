package com.n0hana.echoes_server.classroom;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClassroomMemberRepository extends JpaRepository<ClassroomMember, UUID> {

    boolean existsByClassroomIdAndUserId(UUID classroomId, UUID userId);

    // aluno só pode estar em uma turma
    boolean existsByUserId(UUID userId);

    Optional<ClassroomMember> findByClassroomIdAndUserId(UUID classroomId, UUID userId);

    Page<ClassroomMember> findByClassroomId(UUID classroomId, Pageable pageable);

    List<ClassroomMember> findByClassroomId(UUID classroomId);

    @EntityGraph(attributePaths = {"classroom"})
    List<ClassroomMember> findByUserId(UUID userId);

    void deleteByClassroomId(UUID classroomId);
    void deleteByUserId(UUID userId);
}
