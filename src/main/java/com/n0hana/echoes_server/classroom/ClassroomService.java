package com.n0hana.echoes_server.classroom;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * Service para gestão de operações em turmas do sistema
 * 
 * @author Enrico Bertozzi
 */
@Service
@RequiredArgsConstructor
public class ClassroomService {

    private final ClassroomRepository classroomRepository;

    public Page<ClassroomInfoDTO> findClassroomsByInstitutionId(UUID id, Integer pageNumber, Integer size) {
        Pageable pageable = PageRequest.of(pageNumber, size);
        return classroomRepository.findByClassroomsByInstitution_Id(id, pageable).map(ClassroomInfoDTO::from);
    }
    

}
