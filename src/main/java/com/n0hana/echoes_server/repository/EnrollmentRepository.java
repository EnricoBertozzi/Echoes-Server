package com.n0hana.echoes_server.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.n0hana.echoes_server.model.Classroom;
import com.n0hana.echoes_server.model.Enrollment;
import com.n0hana.echoes_server.model.User;

@Repository
public interface EnrollmentRepository extends JpaRepository<Enrollment, UUID> {
    boolean existsByStudentAndClassroom(User student, Classroom classroom);
    List<Enrollment> findAllByStudent(User student);
}
