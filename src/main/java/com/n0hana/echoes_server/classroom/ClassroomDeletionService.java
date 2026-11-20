package com.n0hana.echoes_server.classroom;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.n0hana.echoes_server.classroom.material.ClassroomMaterialRepository;
import com.n0hana.echoes_server.classroom.material.ClassroomMaterialStorage;

import lombok.RequiredArgsConstructor;

/**
 * Exclusão lógica de turmas, usada tanto pelo endpoint da turma quanto pelo
 * cascade de exclusão da instituição.
 *
 * <p>
 * Remove os vínculos de membros e os materiais, marca a turma como excluída e
 * apaga os arquivos em disco depois do commit.
 * </p>
 */
@Service
@RequiredArgsConstructor
public class ClassroomDeletionService {

    private final ClassroomRepository classroomRepository;
    private final ClassroomMemberRepository memberRepository;
    private final ClassroomMaterialRepository materialRepository;
    private final ClassroomMaterialStorage materialStorage;

    @Transactional
    public ClassroomModel delete(ClassroomModel classroom) {
        UUID classroomId = classroom.getId();

        memberRepository.deleteByClassroomId(classroomId);
        List<String> storedNames = materialRepository.findStoredNamesByClassroomId(classroomId);
        materialRepository.deleteAllByClassroomId(classroomId);
        materialStorage.deleteAfterCommit(storedNames);

        classroom.setDeleted(true);
        return classroomRepository.save(classroom);
    }

    /**
     * Exclui as turmas ainda ativas da instituição. O
     * {@code @SQLRestriction("deleted = false")} da entidade já filtra as
     * turmas excluídas anteriormente.
     */
    @Transactional
    public void deleteByInstitutionId(UUID institutionId) {
        classroomRepository.findByInstitutionId(institutionId).forEach(this::delete);
    }
}
