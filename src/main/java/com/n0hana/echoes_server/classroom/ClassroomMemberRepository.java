package com.n0hana.echoes_server.classroom;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ClassroomMemberRepository extends JpaRepository<ClassroomMember, UUID> {

    boolean existsByClassroomIdAndUserId(UUID classroomId, UUID userId);

    boolean existsByClassroomIdAndUserIdAndRole(UUID classroomId, UUID userId, ClassroomMemberRole role);
}
