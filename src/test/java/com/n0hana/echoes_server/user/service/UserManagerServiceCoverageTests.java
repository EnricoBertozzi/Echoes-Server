package com.n0hana.echoes_server.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.n0hana.echoes_server.auth.device.KnownDeviceService;
import com.n0hana.echoes_server.classroom.ClassroomMemberRepository;
import com.n0hana.echoes_server.infra.javers.anonymization.JaversAnonymizationService;
import com.n0hana.echoes_server.institution.InstitutionModel;
import com.n0hana.echoes_server.institution.InstitutionService;
import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.dto.UpdateInstitutionUserDTO;
import com.n0hana.echoes_server.user.dto.UpdateUserDTO;
import com.n0hana.echoes_server.user.dto.UserDTO;
import com.n0hana.echoes_server.user.exception.EmailAlreadyInUseException;
import com.n0hana.echoes_server.user.exception.InvalidUserTypeException;
import com.n0hana.echoes_server.user.exception.LastAdminException;
import com.n0hana.echoes_server.user.exception.LastManagerException;
import com.n0hana.echoes_server.user.exception.UserNotFoundException;
import com.n0hana.echoes_server.user.model.Admin;
import com.n0hana.echoes_server.user.model.Manager;
import com.n0hana.echoes_server.user.model.Student;
import com.n0hana.echoes_server.user.model.Teacher;
import com.n0hana.echoes_server.user.model.User;
import com.n0hana.echoes_server.user.model.UserRole;

@ExtendWith(MockitoExtension.class)
class UserManagerServiceCoverageTests {

    @Mock
    private UserRepository userRepository;
    @Mock
    private InstitutionService institutionService;
    @Mock
    private ClassroomMemberRepository classroomMemberRepository;
    @Mock
    private JaversAnonymizationService javersAnonymization;
    @Mock
    private KnownDeviceService knownDevices;

    @InjectMocks
    private UserManagerService service;

    private static InstitutionModel institution() {
        InstitutionModel institution = new InstitutionModel();
        institution.setId(UUID.randomUUID());
        return institution;
    }

    private <T extends User> T stored(T user, String name) {
        user.setId(UUID.randomUUID());
        user.setName(name);
        user.setEmail(name.toLowerCase() + "@example.com");
        user.setPassword("hash");
        user.setLoginAttempts(3);
        user.setLockUntil(LocalDateTime.now().plusHours(1));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        return user;
    }

    private void saveReturnsArgument() {
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ------------------------------------------------------------ updates

    @Test
    @DisplayName("updateManager sem instituição anterior atribui a nova sem checar último gestor")
    void updateManagerFirstInstitution() {
        Manager manager = stored(new Manager(), "Gestor");
        InstitutionModel target = institution();
        UpdateInstitutionUserDTO dto = new UpdateInstitutionUserDTO("Novo Nome", target.getId());
        when(institutionService.findById(target.getId())).thenReturn(target);
        saveReturnsArgument();

        UserDTO result = service.updateManager(manager.getId(), dto);

        assertEquals("Novo Nome", result.name());
        assertEquals(target.getId(), result.institutionId());
        assertSame(target, manager.getInstitution());
        verify(userRepository, never()).countActiveManagers(any());
    }

    @Test
    @DisplayName("updateManager sem institutionId só altera o nome")
    void updateManagerNameOnly() {
        InstitutionModel own = institution();
        Manager manager = stored(new Manager(), "Gestor");
        manager.setInstitution(own);
        saveReturnsArgument();

        UserDTO result = service.updateManager(manager.getId(), new UpdateInstitutionUserDTO("Outro", null));

        assertEquals("Outro", result.name());
        assertSame(own, manager.getInstitution());
        verify(institutionService, never()).findById(any());
    }

    @Test
    @DisplayName("updateManager para outra instituição com outro gestor ativo é permitido")
    void updateManagerTransferWithOtherManagers() {
        InstitutionModel own = institution();
        InstitutionModel target = institution();
        Manager manager = stored(new Manager(), "Gestor");
        manager.setInstitution(own);
        when(institutionService.findById(target.getId())).thenReturn(target);
        when(userRepository.countActiveManagers(own.getId())).thenReturn(2L);
        saveReturnsArgument();

        UserDTO result = service.updateManager(manager.getId(), new UpdateInstitutionUserDTO(null, target.getId()));

        assertEquals(target.getId(), result.institutionId());
        assertEquals("Gestor", result.name());
    }

    @Test
    @DisplayName("updateTeacher altera nome e instituição")
    void updateTeacherWithInstitution() {
        Teacher teacher = stored(new Teacher(), "Prof");
        InstitutionModel target = institution();
        when(institutionService.findById(target.getId())).thenReturn(target);
        saveReturnsArgument();

        UserDTO result = service.updateTeacher(teacher.getId(), new UpdateInstitutionUserDTO("Prof Novo", target.getId()));

        assertEquals("Prof Novo", result.name());
        assertEquals(target.getId(), result.institutionId());
        assertEquals(UserRole.TEACHER, result.role());
    }

    @Test
    @DisplayName("updateTeacher sem institutionId mantém instituição e sem nome mantém nome")
    void updateTeacherNothing() {
        Teacher teacher = stored(new Teacher(), "Prof");
        saveReturnsArgument();

        UserDTO result = service.updateTeacher(teacher.getId(), new UpdateInstitutionUserDTO(null, null));

        assertEquals("Prof", result.name());
        assertNull(result.institutionId());
        verify(institutionService, never()).findById(any());
    }

    @Test
    @DisplayName("updateStudent altera nome e instituição")
    void updateStudentWithInstitution() {
        Student student = stored(new Student(), "Aluno");
        InstitutionModel target = institution();
        when(institutionService.findById(target.getId())).thenReturn(target);
        saveReturnsArgument();

        UserDTO result = service.updateStudent(student.getId(), new UpdateInstitutionUserDTO("Aluno Novo", target.getId()));

        assertEquals("Aluno Novo", result.name());
        assertEquals(target.getId(), result.institutionId());
    }

    @Test
    @DisplayName("updateStudent sem institutionId não consulta instituição")
    void updateStudentWithoutInstitution() {
        Student student = stored(new Student(), "Aluno");
        saveReturnsArgument();

        UserDTO result = service.updateStudent(student.getId(), new UpdateInstitutionUserDTO("X", null));

        assertEquals("X", result.name());
        verify(institutionService, never()).findById(any());
    }

    @Test
    @DisplayName("updates lançam UserNotFoundException para id inexistente ou usuário inativo")
    void updatesNotFound() {
        UUID missing = UUID.randomUUID();
        when(userRepository.findById(missing)).thenReturn(Optional.empty());
        Student inactive = stored(new Student(), "Inativo");
        inactive.setActive(false);

        assertThrows(UserNotFoundException.class, () -> service.updateAdmin(missing, new UpdateUserDTO("x")));
        assertThrows(UserNotFoundException.class,
            () -> service.updateStudent(inactive.getId(), new UpdateInstitutionUserDTO("x", null)));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("updates com tipo incorreto lançam InvalidUserTypeException")
    void updatesWrongType() {
        Student student = stored(new Student(), "Aluno");

        assertThrows(InvalidUserTypeException.class, () -> service.updateAdmin(student.getId(), new UpdateUserDTO("x")));
        assertThrows(InvalidUserTypeException.class,
            () -> service.updateManager(student.getId(), new UpdateInstitutionUserDTO("x", null)));
        assertThrows(InvalidUserTypeException.class,
            () -> service.updateTeacher(student.getId(), new UpdateInstitutionUserDTO("x", null)));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateCurrentUser lança UserNotFoundException para e-mail desconhecido")
    void updateCurrentUserNotFound() {
        when(userRepository.findByEmail("ninguem@example.com")).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class,
            () -> service.updateCurrentUser("ninguem@example.com", new UpdateUserDTO("x")));
    }

    @Test
    @DisplayName("updateCurrentUser com nome nulo mantém o nome")
    void updateCurrentUserNullName() {
        Admin admin = new Admin();
        admin.setId(UUID.randomUUID());
        admin.setName("Admin");
        admin.setEmail("admin@example.com");
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        saveReturnsArgument();

        UserDTO result = service.updateCurrentUser("admin@example.com", new UpdateUserDTO(null));

        assertEquals("Admin", result.name());
    }

    // ------------------------------------------------------------ deletes

    @Test
    @DisplayName("deleteAdmin do último administrador ativo lança LastAdminException")
    void deleteLastAdmin() {
        Admin admin = stored(new Admin(), "Admin");
        when(userRepository.countActiveAdmins()).thenReturn(1L);

        assertThrows(LastAdminException.class, () -> service.deleteAdmin(admin.getId()));

        assertTrue(admin.isActive());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("deleteAdmin com outros administradores anonimiza e inativa a conta")
    void deleteAdminAnonymizes() {
        Admin admin = stored(new Admin(), "Admin");
        UUID id = admin.getId();
        when(userRepository.countActiveAdmins()).thenReturn(2L);

        service.deleteAdmin(id);

        assertFalse(admin.isActive());
        assertNull(admin.getName());
        assertNull(admin.getPassword());
        assertNull(admin.getLockUntil());
        assertEquals(0, admin.getLoginAttempts());
        assertTrue(admin.getEmail().startsWith("deleted_user_"));
        assertTrue(admin.getEmail().endsWith("@deleted.local"));
        verify(userRepository).save(admin);
        verify(classroomMemberRepository).deleteByUserId(id);
        verify(javersAnonymization).anonymizeAfterSoftDelete(id);
        verify(knownDevices).forget(id);
    }

    @Test
    @DisplayName("deleteManager remove o vínculo com a instituição e libera turmas")
    void deleteManagerClearsInstitution() {
        InstitutionModel own = institution();
        Manager manager = stored(new Manager(), "Gestor");
        manager.setInstitution(own);
        when(userRepository.countActiveManagers(own.getId())).thenReturn(2L);

        service.deleteManager(manager.getId());

        assertNull(manager.getInstitution());
        assertFalse(manager.isActive());
        verify(classroomMemberRepository).deleteByUserId(manager.getId());
    }

    @Test
    @DisplayName("deleteManager sem instituição não é bloqueado pela regra do último gestor")
    void deleteManagerWithoutInstitution() {
        Manager manager = stored(new Manager(), "Gestor");

        service.deleteManager(manager.getId());

        assertFalse(manager.isActive());
        verify(userRepository, never()).countActiveManagers(any());
    }

    @Test
    @DisplayName("deleteManager do último gestor da instituição lança LastManagerException")
    void deleteLastManager() {
        InstitutionModel own = institution();
        Manager manager = stored(new Manager(), "Gestor");
        manager.setInstitution(own);
        when(userRepository.countActiveManagers(own.getId())).thenReturn(1L);

        assertThrows(LastManagerException.class, () -> service.deleteManager(manager.getId()));
        assertTrue(manager.isActive());
    }

    @Test
    @DisplayName("deleteTeacher anonimiza e desvincula da instituição")
    void deleteTeacher() {
        Teacher teacher = stored(new Teacher(), "Prof");
        teacher.setInstitution(institution());

        service.deleteTeacher(teacher.getId());

        assertFalse(teacher.isActive());
        assertNull(teacher.getInstitution());
        assertNull(teacher.getName());
        verify(userRepository).save(teacher);
    }

    @Test
    @DisplayName("deleteStudent anonimiza e desvincula da instituição")
    void deleteStudent() {
        Student student = stored(new Student(), "Aluno");
        student.setInstitution(institution());

        service.deleteStudent(student.getId());

        assertFalse(student.isActive());
        assertNull(student.getInstitution());
        verify(classroomMemberRepository).deleteByUserId(student.getId());
    }

    @Test
    @DisplayName("deleteTeacher com id de outro tipo lança InvalidUserTypeException")
    void deleteWrongType() {
        Student student = stored(new Student(), "Aluno");

        assertThrows(InvalidUserTypeException.class, () -> service.deleteTeacher(student.getId()));
        assertThrows(InvalidUserTypeException.class, () -> service.deleteManager(student.getId()));
        assertThrows(InvalidUserTypeException.class, () -> service.deleteAdmin(student.getId()));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("deleteCurrentUser lança UserNotFoundException para e-mail desconhecido")
    void deleteCurrentUserNotFound() {
        when(userRepository.findByEmail("x@example.com")).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> service.deleteCurrentUser("x@example.com"));
    }

    @Test
    @DisplayName("deleteCurrentUser de administrador único lança LastAdminException")
    void deleteCurrentUserLastAdmin() {
        Admin admin = new Admin();
        admin.setId(UUID.randomUUID());
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        when(userRepository.countActiveAdmins()).thenReturn(1L);

        assertThrows(LastAdminException.class, () -> service.deleteCurrentUser("admin@example.com"));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("deleteCurrentUser de administrador com outros ativos devolve o id e exclui")
    void deleteCurrentUserAdmin() {
        Admin admin = new Admin();
        admin.setId(UUID.randomUUID());
        admin.setEmail("admin@example.com");
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        when(userRepository.countActiveAdmins()).thenReturn(3L);

        UUID deleted = service.deleteCurrentUser("admin@example.com");

        assertEquals(admin.getId(), deleted);
        assertFalse(admin.isActive());
    }

    @Test
    @DisplayName("deleteCurrentUser de gestor com outro gestor na instituição exclui a conta")
    void deleteCurrentUserManagerWithPeers() {
        InstitutionModel own = institution();
        Manager manager = new Manager();
        manager.setId(UUID.randomUUID());
        manager.setInstitution(own);
        when(userRepository.findByEmail("g@example.com")).thenReturn(Optional.of(manager));
        when(userRepository.countActiveManagers(own.getId())).thenReturn(2L);

        UUID deleted = service.deleteCurrentUser("g@example.com");

        assertEquals(manager.getId(), deleted);
        assertNull(manager.getInstitution());
    }

    @Test
    @DisplayName("softDelete persiste o usuário já anonimizado com e-mail único")
    void anonymizedEmailsAreUnique() {
        Student a = stored(new Student(), "A");
        Student b = stored(new Student(), "B");

        service.deleteStudent(a.getId());
        service.deleteStudent(b.getId());

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository, org.mockito.Mockito.times(2)).save(saved.capture());
        assertNotNull(saved.getAllValues().get(0).getEmail());
        assertFalse(saved.getAllValues().get(0).getEmail().equals(saved.getAllValues().get(1).getEmail()));
    }

    // ------------------------------------------------------------ e-mail

    @Test
    @DisplayName("assertEmailAvailable lança EmailAlreadyInUseException quando já existe")
    void assertEmailTaken() {
        when(userRepository.existsByEmail("a@example.com")).thenReturn(true);
        when(userRepository.existsByEmail("livre@example.com")).thenReturn(false);

        assertThrows(EmailAlreadyInUseException.class, () -> service.assertEmailAvailable("a@example.com"));
        service.assertEmailAvailable("livre@example.com");
    }
}
