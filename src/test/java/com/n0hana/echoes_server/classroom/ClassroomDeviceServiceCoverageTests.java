package com.n0hana.echoes_server.classroom;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.n0hana.echoes_server.classroom.exception.ClassroomForbiddenException;
import com.n0hana.echoes_server.device.DeviceRepository;
import com.n0hana.echoes_server.institution.InstitutionModel;
import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.model.Manager;

@ExtendWith(MockitoExtension.class)
class ClassroomDeviceServiceCoverageTests {

    @Mock private ClassroomRepository classroomRepository;
    @Mock private ClassroomMemberRepository memberRepository;
    @Mock private DeviceRepository deviceRepository;
    @Mock private UserRepository userRepository;
    @InjectMocks private ClassroomDeviceService service;

    @Test
    @DisplayName("Gestor sem instituição não gerencia dispositivos da turma")
    void managerWithoutInstitutionIsForbidden() {
        Manager manager = new Manager();
        manager.setId(UUID.randomUUID());
        manager.setInstitution(null);
        ClassroomModel classroom = ClassroomModel.builder().id(UUID.randomUUID()).title("T").period("2026/01")
                .institution(InstitutionModel.builder().id(UUID.randomUUID()).build()).build();
        when(userRepository.findByEmail("gestor@exemplo.test")).thenReturn(Optional.of(manager));
        when(classroomRepository.findById(classroom.getId())).thenReturn(Optional.of(classroom));

        assertThrows(ClassroomForbiddenException.class,
                () -> service.addDevice("gestor@exemplo.test", classroom.getId(), UUID.randomUUID()));
    }
}
