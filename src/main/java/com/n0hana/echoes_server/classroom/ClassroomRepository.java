package com.n0hana.echoes_server.classroom;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClassroomRepository extends JpaRepository<ClassroomModel, UUID> {

    // admin vê todas as turmas
    Page<ClassroomModel> findByTitleContainingIgnoreCase(String title, Pageable pageable);

    // gestor vê todas as turmas da instituição
    Page<ClassroomModel> findByInstitutionIdAndTitleContainingIgnoreCase(UUID institutionId, String title,
            Pageable pageable);

    // turmas ativas da instituição (usado no cascade de exclusão)
    List<ClassroomModel> findByInstitutionId(UUID institutionId);

    // professor e aluno veem as turmas em que são membros
    @Query("select c from ClassroomModel c where lower(c.title) like lower(concat('%', :name, '%')) "
            + "and c.id in (select m.classroom.id from ClassroomMember m where m.user.id = :userId)")
    Page<ClassroomModel> findByMemberId(@Param("userId") UUID userId, @Param("name") String name,
            Pageable pageable);
}
