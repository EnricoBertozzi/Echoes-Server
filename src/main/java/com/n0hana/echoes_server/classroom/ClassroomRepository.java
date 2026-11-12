package com.n0hana.echoes_server.classroom;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClassroomRepository extends JpaRepository<ClassroomModel, UUID> {
        //gestor ve todas as turmas da intituição
        Page<ClassroomModel> findByInstitutionId(UUID institutionId, Pageable pageable);

        //professor e alunos veem turmas que são membros
        @Query("select c from ClassroomModel c where c.id in "
                        + "(select m.classroom.id from ClassroomMember m where m.user.id = :userId)")
        Page<ClassroomModel> findByMemberId(@Param("userId") UUID userId, Pageable pageable);
}
