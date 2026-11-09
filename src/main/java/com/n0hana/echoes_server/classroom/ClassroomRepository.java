package com.n0hana.echoes_server.classroom;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import com.n0hana.echoes_server.institution.InstitutionModel;


/**
 * Repositório para operações em turmas da aplicação
 * 
 * @author Enrico Bertozzi
 */
public interface ClassroomRepository extends JpaRepository<ClassroomModel, UUID> {

    Page<ClassroomModel> findByClassroomsByInstitution_Id(UUID institutionId, Pageable pageable);
}
