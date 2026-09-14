package com.n0hana.echoes_server.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.n0hana.echoes_server.mfa.TwoFactorDTO;
import com.n0hana.echoes_server.mfa.TwoFactorService;
import com.n0hana.echoes_server.notifier.TwoFactorNotifier;
import com.n0hana.echoes_server.user.dto.CompleteRegistrationDTO;
import com.n0hana.echoes_server.user.dto.CreateInstitutionUserDTO;
import com.n0hana.echoes_server.user.dto.CreateUserDTO;
import com.n0hana.echoes_server.user.dto.PendingRegistrationDTO;
import com.n0hana.echoes_server.user.dto.UpdateInstitutionUserDTO;
import com.n0hana.echoes_server.user.dto.UpdateUserDTO;
import com.n0hana.echoes_server.user.dto.UserDTO;
import com.n0hana.echoes_server.user.exception.EmailAlreadyInUseException;
import com.n0hana.echoes_server.user.exception.ExpiredTwoFactorCodeException;
import com.n0hana.echoes_server.user.exception.InvalidTwoFactorCodeException;
import com.n0hana.echoes_server.user.exception.InvalidUserTypeException;
import com.n0hana.echoes_server.user.exception.RegistrationAlreadyCompletedException;
import com.n0hana.echoes_server.user.exception.UserNotFoundException;

@ExtendWith(MockitoExtension.class)
class UserServiceTests {

    private static final String RAW_PASSWORD = "SenhaForte123!";
    private static final String ENCODED_PASSWORD = "$2a$10$fixedhash";
    private static final String GENERATED_CODE = "123456";

    @Mock
    private UserRepository userRepository;

    @Mock
    private TwoFactorService twoFactorService;

    @Mock
    private PendingRegistrationRepository pendingRegistrationRepository;

    @Mock
    private TwoFactorNotifier notifier;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private final Pageable pageable = PageRequest.of(0, 10);

    @Test
    @DisplayName("Convite de Admin: nada persiste no banco; pendência no Redis com código e e-mail normalizado")
    void createAdminSavesPendingRegistrationWithoutTouchingDatabase() {
        when(userRepository.existsByEmail("joao@example.com")).thenReturn(false);
        when(twoFactorService.generateCode()).thenReturn(GENERATED_CODE);

        PendingRegistrationDTO dto = userService.createAdmin(
                new CreateUserDTO("João", "  JOAO@EXAMPLE.COM  "));

        verify(userRepository, never()).save(any());
        PendingRegistration pending = captureSavedPending();
        assertEquals("João", pending.name());
        assertEquals("joao@example.com", pending.email());
        assertEquals("ADMIN", pending.role());
        assertNull(pending.institutionId());
        assertEquals(GENERATED_CODE, pending.code());
        assertEquals(0, pending.attempts());
        assertCodeValidFor5Minutes(pending);
        assertCodeSentFor("joao@example.com");

        assertEquals("João", dto.name());
        assertEquals("joao@example.com", dto.email());
        assertEquals(UserRole.ADMIN, dto.role());
        assertNull(dto.institutionId());
    }

    @Test
    @DisplayName("Convite de Manager: pendência no Redis com institutionId, sem tocar no banco")
    void createManagerSavesPendingRegistrationWithInstitutionId() {
        UUID institutionId = UUID.randomUUID();
        when(userRepository.existsByEmail("gestor@example.com")).thenReturn(false);
        when(twoFactorService.generateCode()).thenReturn(GENERATED_CODE);

        PendingRegistrationDTO dto = userService.createManager(
                new CreateInstitutionUserDTO("Gestor", "gestor@example.com", institutionId));

        verify(userRepository, never()).save(any());
        PendingRegistration pending = captureSavedPending();
        assertEquals("MANAGER", pending.role());
        assertEquals(institutionId, pending.institutionId());
        assertEquals("gestor@example.com", pending.email());
        assertCodeSentFor("gestor@example.com");

        assertEquals(UserRole.MANAGER, dto.role());
        assertEquals(institutionId, dto.institutionId());
    }

    @Test
    @DisplayName("Convite de Teacher: pendência no Redis com institutionId, sem tocar no banco")
    void createTeacherSavesPendingRegistrationWithInstitutionId() {
        UUID institutionId = UUID.randomUUID();
        when(userRepository.existsByEmail("professor@example.com")).thenReturn(false);
        when(twoFactorService.generateCode()).thenReturn(GENERATED_CODE);

        PendingRegistrationDTO dto = userService.createTeacher(
                new CreateInstitutionUserDTO("Professor", "professor@example.com", institutionId));

        verify(userRepository, never()).save(any());
        PendingRegistration pending = captureSavedPending();
        assertEquals("TEACHER", pending.role());
        assertEquals(institutionId, pending.institutionId());
        assertCodeSentFor("professor@example.com");

        assertEquals(UserRole.TEACHER, dto.role());
        assertEquals(institutionId, dto.institutionId());
    }

    @Test
    @DisplayName("Convite de Student: pendência no Redis com institutionId, sem tocar no banco")
    void createStudentSavesPendingRegistrationWithInstitutionId() {
        UUID institutionId = UUID.randomUUID();
        when(userRepository.existsByEmail("aluno@example.com")).thenReturn(false);
        when(twoFactorService.generateCode()).thenReturn(GENERATED_CODE);

        PendingRegistrationDTO dto = userService.createStudent(
                new CreateInstitutionUserDTO("Aluno", "aluno@example.com", institutionId));

        verify(userRepository, never()).save(any());
        PendingRegistration pending = captureSavedPending();
        assertEquals("STUDENT", pending.role());
        assertEquals(institutionId, pending.institutionId());
        assertCodeSentFor("aluno@example.com");

        assertEquals(UserRole.STUDENT, dto.role());
        assertEquals(institutionId, dto.institutionId());
    }

    @Test
    @DisplayName("E-mail duplicado na criação lança EmailAlreadyInUseException sem salvar pendência nem enviar código")
    void duplicateEmailOnCreateThrowsAndNeverSavesPending() {
        when(userRepository.existsByEmail("joao@example.com")).thenReturn(true);

        assertThrows(EmailAlreadyInUseException.class, () -> userService.createAdmin(
                new CreateUserDTO("João", "JOAO@example.com")));

        verify(pendingRegistrationRepository, never()).save(any());
        verify(notifier, never()).send(any());
    }

    @Test
    @DisplayName("Reconvite de e-mail pendente sobrescreve a pendência e reenvia o código (reenvio)")
    void reinviteOverwritesPendingRegistrationWithNewCode() {
        when(userRepository.existsByEmail("joao@example.com")).thenReturn(false);
        when(twoFactorService.generateCode()).thenReturn(GENERATED_CODE);

        userService.createAdmin(new CreateUserDTO("João", "joao@example.com"));
        userService.createAdmin(new CreateUserDTO("João Silva", "joao@example.com"));

        verify(pendingRegistrationRepository, times(2)).save(any(PendingRegistration.class));
        verify(notifier, times(2)).send(any(TwoFactorDTO.class));
    }

    @Test
    @DisplayName("Código válido: cria o usuário só agora, com senha codificada, e consome o convite")
    void validCodeCreatesUserWithEncodedPasswordAndConsumesInvite() {
        UUID institutionId = UUID.randomUUID();
        when(pendingRegistrationRepository.findByEmail("joao@example.com"))
                .thenReturn(Optional.of(pendingFor("joao@example.com", "STUDENT", institutionId, 0)));
        when(userRepository.existsByEmail("joao@example.com")).thenReturn(false);
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(ENCODED_PASSWORD);
        when(userRepository.save(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));

        UserDTO dto = userService.completeRegistration(
                new CompleteRegistrationDTO("joao@example.com", RAW_PASSWORD, GENERATED_CODE));

        ArgumentCaptor<Student> userCaptor = ArgumentCaptor.forClass(Student.class);
        verify(userRepository).save(userCaptor.capture());
        Student saved = userCaptor.getValue();
        assertEquals("João", saved.getName());
        assertEquals("joao@example.com", saved.getEmail());
        assertEquals(ENCODED_PASSWORD, saved.getPassword());
        assertNotEquals(RAW_PASSWORD, saved.getPassword());
        assertEquals(institutionId, saved.getInstitutionId());
        assertTrue(saved.isActive());

        verify(pendingRegistrationRepository).deleteByEmail("joao@example.com");
        assertEquals("joao@example.com", dto.email());
        assertEquals(UserRole.STUDENT, dto.role());
        assertEquals(institutionId, dto.institutionId());
    }

    @Test
    @DisplayName("completeRegistration normaliza o e-mail antes de buscar a pendência")
    void completeRegistrationNormalizesEmailBeforeLookups() {
        when(pendingRegistrationRepository.findByEmail("joao@example.com"))
                .thenReturn(Optional.of(pendingFor("joao@example.com", "ADMIN", null, 0)));
        when(userRepository.existsByEmail("joao@example.com")).thenReturn(false);
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(ENCODED_PASSWORD);
        when(userRepository.save(any(Admin.class))).thenAnswer(inv -> inv.getArgument(0));

        UserDTO dto = userService.completeRegistration(
                new CompleteRegistrationDTO("  JOAO@Example.COM ", RAW_PASSWORD, GENERATED_CODE));

        verify(pendingRegistrationRepository).findByEmail("joao@example.com");
        verify(pendingRegistrationRepository).deleteByEmail("joao@example.com");
        assertEquals("joao@example.com", dto.email());
    }

    @Test
    @DisplayName("Sem pendência e e-mail desconhecido → UserNotFoundException (404)")
    void unknownEmailWithNoPendingThrowsUserNotFound() {
        when(pendingRegistrationRepository.findByEmail("joao@example.com")).thenReturn(Optional.empty());
        when(userRepository.existsByEmail("joao@example.com")).thenReturn(false);

        assertThrows(UserNotFoundException.class, () -> userService.completeRegistration(
                new CompleteRegistrationDTO("joao@example.com", RAW_PASSWORD, GENERATED_CODE)));
    }

    @Test
    @DisplayName("Sem pendência e e-mail já registrado → RegistrationAlreadyCompletedException (409)")
    void alreadyRegisteredEmailWithNoPendingThrowsRegistrationAlreadyCompleted() {
        when(pendingRegistrationRepository.findByEmail("joao@example.com")).thenReturn(Optional.empty());
        when(userRepository.existsByEmail("joao@example.com")).thenReturn(true);

        assertThrows(RegistrationAlreadyCompletedException.class, () -> userService.completeRegistration(
                new CompleteRegistrationDTO("joao@example.com", RAW_PASSWORD, GENERATED_CODE)));
    }

    @Test
    @DisplayName("Código errado → InvalidTwoFactorCodeException e a tentativa é contada na pendência")
    void wrongCodeThrowsInvalidAndCountsAttempt() {
        when(pendingRegistrationRepository.findByEmail("joao@example.com"))
                .thenReturn(Optional.of(pendingFor("joao@example.com", "STUDENT", null, 1)));

        assertThrows(InvalidTwoFactorCodeException.class, () -> userService.completeRegistration(
                new CompleteRegistrationDTO("joao@example.com", RAW_PASSWORD, "999999")));

        ArgumentCaptor<PendingRegistration> captor = ArgumentCaptor.forClass(PendingRegistration.class);
        verify(pendingRegistrationRepository).savePreservingTtl(captor.capture());
        assertEquals(2, captor.getValue().attempts());
        verify(pendingRegistrationRepository, never()).deleteByEmail(anyString());
    }

    @Test
    @DisplayName("Quinta tentativa errada invalida o convite inteiro (proteção contra força bruta)")
    void fifthWrongAttemptInvalidatesInvite() {
        when(pendingRegistrationRepository.findByEmail("joao@example.com"))
                .thenReturn(Optional.of(pendingFor("joao@example.com", "STUDENT", null,
                        UserService.MAX_VERIFICATION_ATTEMPTS - 1)));

        assertThrows(InvalidTwoFactorCodeException.class, () -> userService.completeRegistration(
                new CompleteRegistrationDTO("joao@example.com", RAW_PASSWORD, "999999")));

        verify(pendingRegistrationRepository).deleteByEmail("joao@example.com");
        verify(pendingRegistrationRepository, never()).savePreservingTtl(any());
    }

    @Test
    @DisplayName("Código correto porém expirado → ExpiredTwoFactorCodeException")
    void expiredCodeThrowsExpiredException() {
        PendingRegistration expired = new PendingRegistration("João", "joao@example.com", "STUDENT", null,
                GENERATED_CODE, Instant.now().minusSeconds(60), 0, Instant.now().minusSeconds(360));
        when(pendingRegistrationRepository.findByEmail("joao@example.com")).thenReturn(Optional.of(expired));

        assertThrows(ExpiredTwoFactorCodeException.class, () -> userService.completeRegistration(
                new CompleteRegistrationDTO("joao@example.com", RAW_PASSWORD, GENERATED_CODE)));
    }

    @Test
    @DisplayName("E-mail registrado entre convite e confirmação → EmailAlreadyInUseException sem criar usuário")
    void emailTakenBetweenInviteAndConfirmationThrowsConflict() {
        when(pendingRegistrationRepository.findByEmail("joao@example.com"))
                .thenReturn(Optional.of(pendingFor("joao@example.com", "STUDENT", null, 0)));
        when(userRepository.existsByEmail("joao@example.com")).thenReturn(true);

        assertThrows(EmailAlreadyInUseException.class, () -> userService.completeRegistration(
                new CompleteRegistrationDTO("joao@example.com", RAW_PASSWORD, GENERATED_CODE)));

        verify(userRepository, never()).save(any());
        verify(pendingRegistrationRepository, never()).deleteByEmail(anyString());
    }

    @Test
    @DisplayName("PATCH apenas com nome: e-mail permanece inalterado")
    void patchNameOnlyKeepsEmail() {
        Admin admin = newAdmin("João", "joao@example.com");
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(userRepository.save(any(Admin.class))).thenAnswer(inv -> inv.getArgument(0));

        UserDTO dto = userService.updateAdmin(admin.getId(), new UpdateUserDTO("João Silva", null));

        assertEquals("João Silva", dto.name());
        assertEquals("joao@example.com", dto.email());
        assertEquals("joao@example.com", admin.getEmail());
    }

    @Test
    @DisplayName("PATCH apenas com e-mail: nome permanece inalterado e e-mail é normalizado")
    void patchEmailOnlyKeepsName() {
        Admin admin = newAdmin("João", "joao@example.com");
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(userRepository.existsByEmail("novo@example.com")).thenReturn(false);
        when(userRepository.save(any(Admin.class))).thenAnswer(inv -> inv.getArgument(0));

        UserDTO dto = userService.updateAdmin(admin.getId(), new UpdateUserDTO(null, "  NOVO@Example.COM "));

        assertEquals("novo@example.com", dto.email());
        assertEquals("João", dto.name());
        assertEquals("novo@example.com", admin.getEmail());
    }

    @Test
    @DisplayName("PATCH apenas com institutionId (Manager): nome e e-mail permanecem inalterados")
    void patchInstitutionIdOnlyKeepsNameAndEmail() {
        Manager manager = newManager("Gestor", "gestor@example.com", UUID.randomUUID());
        UUID newInstitutionId = UUID.randomUUID();
        when(userRepository.findById(manager.getId())).thenReturn(Optional.of(manager));
        when(userRepository.save(any(Manager.class))).thenAnswer(inv -> inv.getArgument(0));

        UserDTO dto = userService.updateManager(manager.getId(),
                new UpdateInstitutionUserDTO(null, null, newInstitutionId));

        assertEquals(newInstitutionId, dto.institutionId());
        assertEquals("Gestor", dto.name());
        assertEquals("gestor@example.com", dto.email());
    }

    @Test
    @DisplayName("PATCH totalmente nulo: nada muda, mas ainda salva e retorna DTO")
    void patchAllNullChangesNothingButStillSaves() {
        Admin admin = newAdmin("João", "joao@example.com");
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(userRepository.save(any(Admin.class))).thenAnswer(inv -> inv.getArgument(0));

        UserDTO dto = userService.updateAdmin(admin.getId(), new UpdateUserDTO(null, null));

        assertEquals("João", dto.name());
        assertEquals("joao@example.com", dto.email());
        verify(userRepository).save(admin);
    }

    @Test
    @DisplayName("Filtro por institutionId é repassado ao repositório; null significa sem filtro")
    void institutionIdFilterIsPassedThroughToRepository() {
        UUID institutionId = UUID.randomUUID();
        when(userRepository.findManagers(institutionId, pageable)).thenReturn(Page.empty());
        when(userRepository.findManagers(null, pageable)).thenReturn(Page.empty());

        userService.findManagers(institutionId, pageable);
        userService.findManagers(null, pageable);

        verify(userRepository).findManagers(institutionId, pageable);
        verify(userRepository).findManagers(null, pageable);
    }

    @Test
    @DisplayName("Paginação: página de entidades é mapeada para Page<UserDTO> com papel e institutionId corretos")
    void pageOfEntitiesIsMappedToPageOfUserDTO() {
        Admin admin1 = newAdmin("Admin Um", "um@example.com");
        Admin admin2 = newAdmin("Admin Dois", "dois@example.com");
        when(userRepository.findAdmins(pageable))
                .thenReturn(new PageImpl<>(List.of(admin1, admin2), pageable, 2));

        Page<UserDTO> result = userService.findAdmins(pageable);

        assertEquals(2, result.getContent().size());
        assertEquals(2, result.getTotalElements());
        UserDTO first = result.getContent().get(0);
        assertEquals(admin1.getId(), first.id());
        assertEquals("Admin Um", first.name());
        assertNull(first.institutionId());
        assertEquals(UserRole.ADMIN, first.role());
    }

    @Test
    @DisplayName("Tipo errado: buscar Manager que é Admin, ou Student que é Teacher, lança InvalidUserTypeException")
    void wrongUserTypeThrowsInvalidUserTypeException() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.of(newAdmin("João", "joao@example.com")));
        assertThrows(InvalidUserTypeException.class, () -> userService.findManagerById(id));

        when(userRepository.findById(id)).thenReturn(Optional.of(newTeacher("Prof", "prof@example.com", null)));
        assertThrows(InvalidUserTypeException.class, () -> userService.findStudentById(id));
    }

    @Test
    @DisplayName("Remoção: inativa (active=false) e salva; usuário já inativo lança UserNotFoundException")
    void deleteSoftDeletesAndInactiveUserIsNotFound() {
        Manager manager = newManager("Gestor", "gestor@example.com", null);
        when(userRepository.findById(manager.getId())).thenReturn(Optional.of(manager));

        userService.deleteManager(manager.getId());

        ArgumentCaptor<Manager> userCaptor = ArgumentCaptor.forClass(Manager.class);
        verify(userRepository).save(userCaptor.capture());
        assertFalse(userCaptor.getValue().isActive());

        Admin inactiveAdmin = newAdmin("João", "joao@example.com");
        inactiveAdmin.setActive(false);
        when(userRepository.findById(inactiveAdmin.getId())).thenReturn(Optional.of(inactiveAdmin));
        assertThrows(UserNotFoundException.class, () -> userService.findAdminById(inactiveAdmin.getId()));
    }

    @Test
    @DisplayName("Update para e-mail já usado lança EmailAlreadyInUseException; mesmo e-mail (no-op) é aceito sem verificação")
    void updateToUsedEmailThrowsAndSameEmailIsAcceptedWithoutCheck() {
        Admin admin = newAdmin("João", "joao@example.com");
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(userRepository.existsByEmail("novo@example.com")).thenReturn(true);
        when(userRepository.save(any(Admin.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThrows(EmailAlreadyInUseException.class, () -> userService.updateAdmin(
                admin.getId(), new UpdateUserDTO(null, "novo@example.com")));

        UserDTO dto = userService.updateAdmin(admin.getId(), new UpdateUserDTO(null, "JOAO@example.com"));

        assertEquals("joao@example.com", dto.email());
        verify(userRepository, times(1)).existsByEmail(anyString());
        verify(userRepository, never()).existsByEmail("joao@example.com");
    }

    private PendingRegistration pendingFor(String email, String role, UUID institutionId, int attempts) {
        return new PendingRegistration("João", email, role, institutionId,
                GENERATED_CODE, Instant.now().plusSeconds(300), attempts, Instant.now());
    }

    private PendingRegistration captureSavedPending() {
        ArgumentCaptor<PendingRegistration> captor = ArgumentCaptor.forClass(PendingRegistration.class);
        verify(pendingRegistrationRepository).save(captor.capture());
        return captor.getValue();
    }

    private void assertCodeSentFor(String email) {
        ArgumentCaptor<TwoFactorDTO> tokenCaptor = ArgumentCaptor.forClass(TwoFactorDTO.class);
        verify(notifier).send(tokenCaptor.capture());
        TwoFactorDTO token = tokenCaptor.getValue();
        assertEquals(email, token.email());
        assertEquals(GENERATED_CODE, token.code());
        assertTrue(token.expiresAt().isAfter(Instant.now().plusSeconds(290)));
    }

    private void assertCodeValidFor5Minutes(PendingRegistration pending) {
        assertTrue(pending.expiresAt().isAfter(Instant.now().plusSeconds(290)));
        assertTrue(pending.expiresAt().isBefore(Instant.now().plusSeconds(310)));
    }

    private Admin newAdmin(String name, String email) {
        Admin admin = new Admin();
        admin.setId(UUID.randomUUID());
        admin.setName(name);
        admin.setEmail(email);
        return admin;
    }

    private Manager newManager(String name, String email, UUID institutionId) {
        Manager manager = new Manager();
        manager.setId(UUID.randomUUID());
        manager.setName(name);
        manager.setEmail(email);
        manager.setInstitutionId(institutionId);
        return manager;
    }

    private Teacher newTeacher(String name, String email, UUID institutionId) {
        Teacher teacher = new Teacher();
        teacher.setId(UUID.randomUUID());
        teacher.setName(name);
        teacher.setEmail(email);
        teacher.setInstitutionId(institutionId);
        return teacher;
    }

    private Student newStudent(String name, String email, UUID institutionId) {
        Student student = new Student();
        student.setId(UUID.randomUUID());
        student.setName(name);
        student.setEmail(email);
        student.setInstitutionId(institutionId);
        return student;
    }
}
