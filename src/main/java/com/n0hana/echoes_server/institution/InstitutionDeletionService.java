package com.n0hana.echoes_server.institution;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.n0hana.echoes_server.classroom.ClassroomDeletionService;
import com.n0hana.echoes_server.device.DeviceLifecycleService;
import com.n0hana.echoes_server.user.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Orquestra o desligamento de uma instituição excluída.
 *
 * <p>
 * Tudo acontece na mesma transação da exclusão: a instituição é anonimizada,
 * as turmas são excluídas, os dispositivos são inativados e os usuários perdem
 * o acesso com {@code active = false}, sem exclusão dos registros.
 * </p>
 */
@Service
@RequiredArgsConstructor
public class InstitutionDeletionService {

    private final InstitutionAnonymizer anonymizer;
    private final ClassroomDeletionService classroomDeletion;
    private final DeviceLifecycleService deviceLifecycle;
    private final UserRepository userRepository;

    @Transactional
    public void delete(InstitutionModel institution) {
        UUID institutionId = institution.getId();

        anonymizer.anonymize(institution);
        classroomDeletion.deleteByInstitutionId(institutionId);
        deviceLifecycle.deactivateByInstitution(institutionId);
        userRepository.deactivateByInstitutionId(institutionId);
    }
}
