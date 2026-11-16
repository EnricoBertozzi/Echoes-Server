package com.n0hana.echoes_server.classroom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.n0hana.echoes_server.classroom.exception.ClassroomArchivedException;
import com.n0hana.echoes_server.classroom.exception.ClassroomForbiddenException;
import com.n0hana.echoes_server.classroom.exception.ClassroomNotFoundException;
import com.n0hana.echoes_server.device.DeviceModel;
import com.n0hana.echoes_server.device.DeviceRepository;
import com.n0hana.echoes_server.device.DeviceStatus;
import com.n0hana.echoes_server.device.exception.DeviceNotFoundException;
import com.n0hana.echoes_server.institution.InstitutionModel;
import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.model.Admin;
import com.n0hana.echoes_server.user.model.Manager;
import com.n0hana.echoes_server.user.model.Student;
import com.n0hana.echoes_server.user.model.Teacher;

@ExtendWith(MockitoExtension.class)
class ClassroomDeviceServiceTests {

    private static final String EMAIL = "usuario@email.com";

    @Mock
    private ClassroomRepository classroomRepository;

    @Mock
    private ClassroomMemberRepository memberRepository;

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ClassroomDeviceService service;

    // adicionar

    @Test
    @DisplayName("Gestor adiciona dispositivo da própria instituição na turma")
    void managerAddsDevice() {
        InstitutionModel institution = institution();
        ClassroomModel classroom = classroom(institution, false);
        DeviceModel device = device(institution);
        mockManager(manager(institution), classroom);
        when(deviceRepository.findById(device.getId())).thenReturn(Optional.of(device));

        ClassroomModel result = service.addDevice(EMAIL, classroom.getId(), device.getId());

        assertSame(classroom, result);
        assertSame(classroom, device.getClassroom());
        verify(deviceRepository).save(device);
    }

    @Test
    @DisplayName("Dispositivo que estava em outra turma passa para a nova")
    void deviceMovesToAnotherClassroom() {
        InstitutionModel institution = institution();
        ClassroomModel oldClassroom = classroom(institution, false);
        ClassroomModel newClassroom = classroom(institution, false);
        DeviceModel device = device(institution);
        device.setClassroom(oldClassroom);
        mockManager(manager(institution), newClassroom);
        when(deviceRepository.findById(device.getId())).thenReturn(Optional.of(device));

        service.addDevice(EMAIL, newClassroom.getId(), device.getId());

        assertSame(newClassroom, device.getClassroom());
        verify(deviceRepository).save(device);
    }

    @Test
    @DisplayName("Não adiciona dispositivo de outra instituição")
    void doesNotAddDeviceFromAnotherInstitution() {
        InstitutionModel institution = institution();
        ClassroomModel classroom = classroom(institution, false);
        DeviceModel device = device(institution());
        mockManager(manager(institution), classroom);
        when(deviceRepository.findById(device.getId())).thenReturn(Optional.of(device));

        assertThrows(ClassroomForbiddenException.class,
                () -> service.addDevice(EMAIL, classroom.getId(), device.getId()));

        assertNull(device.getClassroom());
        verify(deviceRepository, never()).save(any());
    }

    @Test
    @DisplayName("Não adiciona dispositivo sem instituição (pendente)")
    void doesNotAddDeviceWithoutInstitution() {
        InstitutionModel institution = institution();
        ClassroomModel classroom = classroom(institution, false);
        DeviceModel device = device(null);
        mockManager(manager(institution), classroom);
        when(deviceRepository.findById(device.getId())).thenReturn(Optional.of(device));

        assertThrows(ClassroomForbiddenException.class,
                () -> service.addDevice(EMAIL, classroom.getId(), device.getId()));

        verify(deviceRepository, never()).save(any());
    }

    @Test
    @DisplayName("Não adiciona dispositivo inexistente")
    void doesNotAddUnknownDevice() {
        InstitutionModel institution = institution();
        ClassroomModel classroom = classroom(institution, false);
        UUID deviceId = UUID.randomUUID();
        mockManager(manager(institution), classroom);
        when(deviceRepository.findById(deviceId)).thenReturn(Optional.empty());

        assertThrows(DeviceNotFoundException.class, () -> service.addDevice(EMAIL, classroom.getId(), deviceId));

        verify(deviceRepository, never()).save(any());
    }

    @Test
    @DisplayName("Turma arquivada não aceita dispositivos")
    void archivedClassroomDoesNotAcceptDevices() {
        InstitutionModel institution = institution();
        ClassroomModel classroom = classroom(institution, true);
        mockManager(manager(institution), classroom);

        assertThrows(ClassroomArchivedException.class,
                () -> service.addDevice(EMAIL, classroom.getId(), UUID.randomUUID()));

        verifyNoInteractions(deviceRepository);
    }

    @Test
    @DisplayName("Professor não adiciona dispositivo")
    void teacherCannotAddDevice() {
        InstitutionModel institution = institution();
        ClassroomModel classroom = classroom(institution, false);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(teacher(institution)));
        when(classroomRepository.findById(classroom.getId())).thenReturn(Optional.of(classroom));

        assertThrows(ClassroomForbiddenException.class,
                () -> service.addDevice(EMAIL, classroom.getId(), UUID.randomUUID()));

        verifyNoInteractions(deviceRepository);
    }

    @Test
    @DisplayName("Gestor de outra instituição não adiciona dispositivo")
    void managerFromAnotherInstitutionCannotAddDevice() {
        ClassroomModel classroom = classroom(institution(), false);
        mockManager(manager(institution()), classroom);

        assertThrows(ClassroomForbiddenException.class,
                () -> service.addDevice(EMAIL, classroom.getId(), UUID.randomUUID()));

        verifyNoInteractions(deviceRepository);
    }

    // remover

    @Test
    @DisplayName("Gestor remove o dispositivo da turma")
    void managerRemovesDevice() {
        InstitutionModel institution = institution();
        ClassroomModel classroom = classroom(institution, false);
        DeviceModel device = device(institution);
        device.setClassroom(classroom);
        mockManager(manager(institution), classroom);
        when(deviceRepository.findById(device.getId())).thenReturn(Optional.of(device));

        ClassroomModel result = service.removeDevice(EMAIL, classroom.getId(), device.getId());

        assertSame(classroom, result);
        assertNull(device.getClassroom());
        verify(deviceRepository).save(device);
    }

    @Test
    @DisplayName("Não remove dispositivo que não está em turma nenhuma")
    void doesNotRemoveDeviceWithoutClassroom() {
        InstitutionModel institution = institution();
        ClassroomModel classroom = classroom(institution, false);
        DeviceModel device = device(institution);
        mockManager(manager(institution), classroom);
        when(deviceRepository.findById(device.getId())).thenReturn(Optional.of(device));

        assertThrows(ClassroomNotFoundException.class,
                () -> service.removeDevice(EMAIL, classroom.getId(), device.getId()));

        verify(deviceRepository, never()).save(any());
    }

    @Test
    @DisplayName("Não remove dispositivo que está em outra turma")
    void doesNotRemoveDeviceFromAnotherClassroom() {
        InstitutionModel institution = institution();
        ClassroomModel classroom = classroom(institution, false);
        DeviceModel device = device(institution);
        device.setClassroom(classroom(institution, false));
        mockManager(manager(institution), classroom);
        when(deviceRepository.findById(device.getId())).thenReturn(Optional.of(device));

        assertThrows(ClassroomNotFoundException.class,
                () -> service.removeDevice(EMAIL, classroom.getId(), device.getId()));

        verify(deviceRepository, never()).save(any());
    }

    @Test
    @DisplayName("Professor não remove dispositivo")
    void teacherCannotRemoveDevice() {
        InstitutionModel institution = institution();
        ClassroomModel classroom = classroom(institution, false);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(teacher(institution)));
        when(classroomRepository.findById(classroom.getId())).thenReturn(Optional.of(classroom));

        assertThrows(ClassroomForbiddenException.class,
                () -> service.removeDevice(EMAIL, classroom.getId(), UUID.randomUUID()));

        verifyNoInteractions(deviceRepository);
    }

    // listar

    @Test
    @DisplayName("Admin lista os dispositivos da turma")
    void adminListsDevices() {
        ClassroomModel classroom = classroom(institution(), false);
        List<DeviceModel> devices = List.of(device(classroom.getInstitution()));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(admin()));
        when(classroomRepository.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        when(deviceRepository.findByClassroomIdOrderByNameAsc(classroom.getId())).thenReturn(devices);

        assertEquals(devices, service.findDevices(EMAIL, classroom.getId()));
    }

    @Test
    @DisplayName("Gestor da instituição lista os dispositivos da turma")
    void managerListsDevices() {
        InstitutionModel institution = institution();
        ClassroomModel classroom = classroom(institution, false);
        List<DeviceModel> devices = List.of(device(institution));
        mockManager(manager(institution), classroom);
        when(deviceRepository.findByClassroomIdOrderByNameAsc(classroom.getId())).thenReturn(devices);

        assertEquals(devices, service.findDevices(EMAIL, classroom.getId()));
    }

    @Test
    @DisplayName("Professor da turma lista os dispositivos")
    void teacherMemberListsDevices() {
        InstitutionModel institution = institution();
        ClassroomModel classroom = classroom(institution, false);
        Teacher teacher = teacher(institution);
        List<DeviceModel> devices = List.of(device(institution));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(teacher));
        when(classroomRepository.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        when(memberRepository.existsByClassroomIdAndUserId(classroom.getId(), teacher.getId())).thenReturn(true);
        when(deviceRepository.findByClassroomIdOrderByNameAsc(classroom.getId())).thenReturn(devices);

        assertEquals(devices, service.findDevices(EMAIL, classroom.getId()));
    }

    @Test
    @DisplayName("Professor que não é da turma não lista os dispositivos")
    void teacherNotMemberCannotListDevices() {
        InstitutionModel institution = institution();
        ClassroomModel classroom = classroom(institution, false);
        Teacher teacher = teacher(institution);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(teacher));
        when(classroomRepository.findById(classroom.getId())).thenReturn(Optional.of(classroom));
        when(memberRepository.existsByClassroomIdAndUserId(classroom.getId(), teacher.getId())).thenReturn(false);

        assertThrows(ClassroomForbiddenException.class, () -> service.findDevices(EMAIL, classroom.getId()));

        verifyNoInteractions(deviceRepository);
    }

    @Test
    @DisplayName("Aluno não lista os dispositivos")
    void studentCannotListDevices() {
        InstitutionModel institution = institution();
        ClassroomModel classroom = classroom(institution, false);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(student(institution)));
        when(classroomRepository.findById(classroom.getId())).thenReturn(Optional.of(classroom));

        assertThrows(ClassroomForbiddenException.class, () -> service.findDevices(EMAIL, classroom.getId()));

        verifyNoInteractions(deviceRepository);
    }

    @Test
    @DisplayName("Gestor de outra instituição não lista os dispositivos")
    void managerFromAnotherInstitutionCannotListDevices() {
        ClassroomModel classroom = classroom(institution(), false);
        mockManager(manager(institution()), classroom);

        assertThrows(ClassroomForbiddenException.class, () -> service.findDevices(EMAIL, classroom.getId()));

        verifyNoInteractions(deviceRepository);
    }

    // apoio

    private void mockManager(Manager manager, ClassroomModel classroom) {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(manager));
        when(classroomRepository.findById(classroom.getId())).thenReturn(Optional.of(classroom));
    }

    private InstitutionModel institution() {
        return InstitutionModel.builder().id(UUID.randomUUID()).active(true).build();
    }

    private Admin admin() {
        Admin admin = new Admin();
        admin.setId(UUID.randomUUID());
        return admin;
    }

    private Manager manager(InstitutionModel institution) {
        Manager manager = new Manager();
        manager.setId(UUID.randomUUID());
        manager.setEmail(EMAIL);
        manager.setInstitution(institution);
        return manager;
    }

    private Teacher teacher(InstitutionModel institution) {
        Teacher teacher = new Teacher();
        teacher.setId(UUID.randomUUID());
        teacher.setEmail(EMAIL);
        teacher.setInstitution(institution);
        return teacher;
    }

    private Student student(InstitutionModel institution) {
        Student student = new Student();
        student.setId(UUID.randomUUID());
        student.setEmail(EMAIL);
        student.setInstitution(institution);
        return student;
    }

    private ClassroomModel classroom(InstitutionModel institution, boolean archived) {
        return ClassroomModel.builder()
                .id(UUID.randomUUID())
                .title("Turma")
                .period("2026/01")
                .institution(institution)
                .archived(archived)
                .build();
    }

    private DeviceModel device(InstitutionModel institution) {
        return DeviceModel.builder()
                .id(UUID.randomUUID())
                .hardwareId("AA:BB:CC:DD:EE:FF")
                .publicKey("pem")
                .status(DeviceStatus.ACTIVE)
                .institution(institution)
                .build();
    }

    // disponíveis

    @Test
    @DisplayName("Gestor lista os dispositivos ativos disponíveis da própria instituição")
    void managerListsAvailableDevices() {
        InstitutionModel institution = institution();
        ClassroomModel classroom = classroom(institution, false);
        DeviceModel device = device(institution);
        mockManager(manager(institution), classroom);
        when(deviceRepository.findAvailableForClassroom(institution.getId(), DeviceStatus.ACTIVE, classroom.getId()))
                .thenReturn(List.of(device));

        List<DeviceModel> result = service.findAvailableDevices(EMAIL, classroom.getId());

        assertEquals(List.of(device), result);
    }

    @Test
    @DisplayName("Gestor de outra instituição não lista dispositivos disponíveis")
    void otherInstitutionManagerCannotListAvailableDevices() {
        ClassroomModel classroom = classroom(institution(), false);
        mockManager(manager(institution()), classroom);

        assertThrows(ClassroomForbiddenException.class,
                () -> service.findAvailableDevices(EMAIL, classroom.getId()));

        verifyNoInteractions(deviceRepository);
    }

    @Test
    @DisplayName("Professor não lista dispositivos disponíveis")
    void teacherCannotListAvailableDevices() {
        InstitutionModel institution = institution();
        ClassroomModel classroom = classroom(institution, false);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(teacher(institution)));
        when(classroomRepository.findById(classroom.getId())).thenReturn(Optional.of(classroom));

        assertThrows(ClassroomForbiddenException.class,
                () -> service.findAvailableDevices(EMAIL, classroom.getId()));

        verifyNoInteractions(deviceRepository);
    }
}
