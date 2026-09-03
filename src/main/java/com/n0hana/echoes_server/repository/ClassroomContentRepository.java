package com.n0hana.echoes_server.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.n0hana.echoes_server.model.Classroom;
import com.n0hana.echoes_server.model.ClassroomContent;

@Repository
public interface ClassroomContentRepository extends JpaRepository<ClassroomContent, UUID> {
    List<ClassroomContent> findAllByClassroomOrderByCreatedAtDesc(Classroom classroom);
}
