package com.n0hana.echoes_server.classroom;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.n0hana.echoes_server.classroom.exception.ClassroomArchivedException;
import com.n0hana.echoes_server.classroom.exception.ClassroomForbiddenException;
import com.n0hana.echoes_server.classroom.exception.ClassroomNotFoundException;
import com.n0hana.echoes_server.device.DeviceModel;
import com.n0hana.echoes_server.device.DeviceRepository;
import com.n0hana.echoes_server.device.exception.DeviceNotFoundException;
import com.n0hana.echoes_server.infra.logs.Auditable;
import com.n0hana.echoes_server.institution.InstitutionModel;
import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.exception.UserNotFoundException;
import com.n0hana.echoes_server.user.model.Admin;
import com.n0hana.echoes_server.user.model.Manager;
import com.n0hana.echoes_server.user.model.Teacher;
import com.n0hana.echoes_server.user.model.User;

import lombok.RequiredArgsConstructor;

// dispositivos da turma: um dispositivo fica em uma turma por vez, a turma pode ter vários
@Service
@RequiredArgsConstructor
public class ClassroomDeviceService {

    private final ClassroomRepository classroomRepository;
    private final ClassroomMemberRepository memberRepository;
    private final DeviceRepository deviceRepository;
    private final UserRepository userRepository;

    // admin, gestor da instituição e professor da turma veem os dispositivos
    @Transactional(readOnly = true)
    public List<DeviceModel> findDevices(String email, UUID id) {
        User user = findUser(email);
        ClassroomModel classroom = findClassroom(id);

        boolean teacherMember = user instanceof Teacher
                && memberRepository.existsByClassroomIdAndUserId(id, user.getId());

        if (!(user instanceof Admin) && !isManagerOf(user, classroom) && !teacherMember)
            throw new ClassroomForbiddenException("Sem acesso aos dispositivos desta turma");

        return deviceRepository.findByClassroomIdOrderByNameAsc(id);
    }

    // se o dispositivo já estava em outra turma da instituição, ele passa para esta
    @Transactional
    @Auditable(action = "ADD_DEVICE", entity = "Classroom", entityId = "#id", institutionId = "#result.institution.id")
    public ClassroomModel addDevice(String email, UUID id, UUID deviceId) {
        ClassroomModel classroom = findManagedClassroom(email, id);

        if (classroom.isArchived())
            throw new ClassroomArchivedException("Turma arquivada não aceita novos dispositivos");

        DeviceModel device = deviceRepository.findById(deviceId).orElseThrow(DeviceNotFoundException::new);
        InstitutionModel institution = device.getInstitution();

        // dispositivo pendente (sem instituição) ou de outra instituição não entra
        if (institution == null || !institution.getId().equals(classroom.getInstitution().getId()))
            throw new ClassroomForbiddenException("Dispositivo inválido para esta turma");

        device.setClassroom(classroom);
        deviceRepository.save(device);
        return classroom;
    }

    @Transactional
    @Auditable(action = "REMOVE_DEVICE", entity = "Classroom", entityId = "#id", institutionId = "#result.institution.id")
    public ClassroomModel removeDevice(String email, UUID id, UUID deviceId) {
        ClassroomModel classroom = findManagedClassroom(email, id);

        DeviceModel device = deviceRepository.findById(deviceId).orElseThrow(DeviceNotFoundException::new);

        if (device.getClassroom() == null || !device.getClassroom().getId().equals(id))
            throw new ClassroomNotFoundException("Dispositivo não encontrado na turma");

        device.setClassroom(null);
        deviceRepository.save(device);
        return classroom;
    }

    // busca a turma e garante que quem pediu é o gestor da instituição dela
    private ClassroomModel findManagedClassroom(String email, UUID id) {
        User user = findUser(email);
        ClassroomModel classroom = findClassroom(id);

        if (!isManagerOf(user, classroom))
            throw new ClassroomForbiddenException("Apenas o gestor da instituição pode executar esta ação");

        return classroom;
    }

    private boolean isManagerOf(User user, ClassroomModel classroom) {
        if (!(user instanceof Manager manager))
            return false;
        InstitutionModel institution = manager.getInstitution();
        return institution != null && institution.getId().equals(classroom.getInstitution().getId());
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email).orElseThrow(UserNotFoundException::new);
    }

    private ClassroomModel findClassroom(UUID id) {
        return classroomRepository.findById(id).orElseThrow(ClassroomNotFoundException::new);
    }
}
