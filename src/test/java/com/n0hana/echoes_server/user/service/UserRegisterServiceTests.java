package com.n0hana.echoes_server.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.n0hana.echoes_server.infra.notifier.NotifierService;
import com.n0hana.echoes_server.institution.InstitutionModel;
import com.n0hana.echoes_server.institution.InstitutionService;
import com.n0hana.echoes_server.mfa.TwoFactorService;
import com.n0hana.echoes_server.term.TermService;
import com.n0hana.echoes_server.term.model.DocumentType;
import com.n0hana.echoes_server.user.PendingRegistration;
import com.n0hana.echoes_server.user.PendingRegistrationRepository;
import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.dto.CompleteRegistrationDTO;
import com.n0hana.echoes_server.user.dto.CreateInstitutionUserDTO;
import com.n0hana.echoes_server.user.dto.CreateUserDTO;
import com.n0hana.echoes_server.user.dto.PendingRegistrationDTO;
import com.n0hana.echoes_server.user.dto.UserDTO;
import com.n0hana.echoes_server.user.exception.EmailAlreadyInUseException;
import com.n0hana.echoes_server.user.exception.ExpiredTwoFactorCodeException;
import com.n0hana.echoes_server.user.exception.InvalidTwoFactorCodeException;
import com.n0hana.echoes_server.user.exception.RegistrationAlreadyCompletedException;
import com.n0hana.echoes_server.user.exception.RequiredTermsNotAcceptedException;
import com.n0hana.echoes_server.user.exception.UserNotFoundException;
import com.n0hana.echoes_server.user.model.Admin;
import com.n0hana.echoes_server.user.model.Student;
import com.n0hana.echoes_server.user.model.UserRole;

@ExtendWith(MockitoExtension.class)
class UserRegisterServiceTests {

    private static final String RAW_PASSWORD = "SenhaForte123!";
    private static final String ENCODED_PASSWORD = "$2a$10$fixedhash";
    private static final String GENERATED_CODE = "123456";

    private static final List<DocumentType> ALL_REQUIRED_TERMS = List.of(
            DocumentType.TERMS_OF_USE,
            DocumentType.PRIVACY_POLICY,
            DocumentType.COOKIES_POLICY);

    @Mock
    private UserRepository userRepository;

    @Mock
    private TwoFactorService twoFactorService;

    @Mock
    private PendingRegistrationRepository pendingRegistrationRepository;

    @Mock
    private NotifierService notifier;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TermService termService;

    @Mock
    private InstitutionService institutionService;

    @InjectMocks
    private UserRegisterService userService;

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
        assertCodeValidFor7Days(pending);
        assertInviteSentFor("joao@example.com");

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
        assertInviteSentFor("gestor@example.com");

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
        assertInviteSentFor("professor@example.com");

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
        assertInviteSentFor("aluno@example.com");

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
        verify(notifier, never()).sendRegisterInvite(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Reconvite de e-mail pendente sobrescreve a pendência e reenvia o código (reenvio)")
    void reinviteOverwritesPendingRegistrationWithNewCode() {
        when(userRepository.existsByEmail("joao@example.com")).thenReturn(false);
        when(twoFactorService.generateCode()).thenReturn(GENERATED_CODE);

        userService.createAdmin(new CreateUserDTO("João", "joao@example.com"));
        userService.createAdmin(new CreateUserDTO("João Silva", "joao@example.com"));

        verify(pendingRegistrationRepository, times(2)).save(any(PendingRegistration.class));
        verify(notifier, times(2)).sendRegisterInvite(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Código válido: cria o usuário só agora, com senha codificada, e consome o convite")
    void validCodeCreatesUserWithEncodedPasswordAndConsumesInvite() {
        UUID institutionId = UUID.randomUUID();
        InstitutionModel institution = new InstitutionModel();
        institution.setId(institutionId);

        when(pendingRegistrationRepository.findByEmail("joao@example.com"))
                .thenReturn(Optional.of(pendingFor("joao@example.com", "STUDENT", institutionId, 0)));
        when(userRepository.existsByEmail("joao@example.com")).thenReturn(false);
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(ENCODED_PASSWORD);
        when(userRepository.save(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));
        when(institutionService.findById(institutionId)).thenReturn(institution);

        UserDTO dto = userService.completeRegistration(
                new CompleteRegistrationDTO("joao@example.com", RAW_PASSWORD, GENERATED_CODE,
                        ALL_REQUIRED_TERMS));

        ArgumentCaptor<Student> userCaptor = ArgumentCaptor.forClass(Student.class);
        verify(userRepository).save(userCaptor.capture());
        Student saved = userCaptor.getValue();
        assertEquals("João", saved.getName());
        assertEquals("joao@example.com", saved.getEmail());
        assertEquals(ENCODED_PASSWORD, saved.getPassword());
        assertNotEquals(RAW_PASSWORD, saved.getPassword());
        assertEquals(institutionId, saved.getInstitution().getId());
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
                new CompleteRegistrationDTO("  JOAO@Example.COM ", RAW_PASSWORD, GENERATED_CODE,
                        ALL_REQUIRED_TERMS));

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
                new CompleteRegistrationDTO("joao@example.com", RAW_PASSWORD, GENERATED_CODE,
                        ALL_REQUIRED_TERMS)));
    }

    @Test
    @DisplayName("Sem pendência e e-mail já registrado → RegistrationAlreadyCompletedException (409)")
    void alreadyRegisteredEmailWithNoPendingThrowsRegistrationAlreadyCompleted() {
        when(pendingRegistrationRepository.findByEmail("joao@example.com")).thenReturn(Optional.empty());
        when(userRepository.existsByEmail("joao@example.com")).thenReturn(true);

        assertThrows(RegistrationAlreadyCompletedException.class, () -> userService.completeRegistration(
                new CompleteRegistrationDTO("joao@example.com", RAW_PASSWORD, GENERATED_CODE,
                        ALL_REQUIRED_TERMS)));
    }

    @Test
    @DisplayName("Código errado → InvalidTwoFactorCodeException e a tentativa é contada na pendência")
    void wrongCodeThrowsInvalidAndCountsAttempt() {
        when(pendingRegistrationRepository.findByEmail("joao@example.com"))
                .thenReturn(Optional.of(pendingFor("joao@example.com", "STUDENT", null, 1)));

        assertThrows(InvalidTwoFactorCodeException.class, () -> userService.completeRegistration(
                new CompleteRegistrationDTO("joao@example.com", RAW_PASSWORD, "999999",
                        ALL_REQUIRED_TERMS)));

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
                        UserRegisterService.MAX_VERIFICATION_ATTEMPTS - 1)));

        assertThrows(InvalidTwoFactorCodeException.class, () -> userService.completeRegistration(
                new CompleteRegistrationDTO("joao@example.com", RAW_PASSWORD, "999999",
                        ALL_REQUIRED_TERMS)));

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
                new CompleteRegistrationDTO("joao@example.com", RAW_PASSWORD, GENERATED_CODE,
                        ALL_REQUIRED_TERMS)));
    }

    @Test
    @DisplayName("E-mail registrado entre convite e confirmação → EmailAlreadyInUseException sem criar usuário")
    void emailTakenBetweenInviteAndConfirmationThrowsConflict() {
        when(pendingRegistrationRepository.findByEmail("joao@example.com"))
                .thenReturn(Optional.of(pendingFor("joao@example.com", "STUDENT", null, 0)));
        when(userRepository.existsByEmail("joao@example.com")).thenReturn(true);

        assertThrows(EmailAlreadyInUseException.class, () -> userService.completeRegistration(
                new CompleteRegistrationDTO("joao@example.com", RAW_PASSWORD, GENERATED_CODE,
                        ALL_REQUIRED_TERMS)));

        verify(userRepository, never()).save(any());
        verify(pendingRegistrationRepository, never()).deleteByEmail(anyString());
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

    private void assertInviteSentFor(String email) {
        verify(notifier).sendRegisterInvite(
                email,
                GENERATED_CODE,
                email);
    }

    private void assertCodeValidFor7Days(PendingRegistration pending) {
        // Verifica se o estado do pedido em relação a uma hora antes da data de
        // expiração
        assertTrue(pending.expiresAt().isAfter(Instant.now().plus(167, ChronoUnit.HOURS)));

        // Verifica se o estado do pedido em relação a uma hora após da data de
        // expiração
        assertTrue(pending.expiresAt().isBefore(Instant.now().plus(169, ChronoUnit.HOURS)));
    }

    @Test
    @DisplayName("Cadastro sem termos obrigatórios lança RequiredTermsNotAcceptedException e não cria User")
    void missingRequiredTermsRejectsRegistration() {
        doThrow(new RequiredTermsNotAcceptedException(
                List.of(DocumentType.TERMS_OF_USE, DocumentType.PRIVACY_POLICY,
                        DocumentType.COOKIES_POLICY)))
                .when(termService).validateRequiredAccepted(any());

        var dto = new CompleteRegistrationDTO(
                "joao@example.com", RAW_PASSWORD, GENERATED_CODE,
                List.of());

        assertThrows(RequiredTermsNotAcceptedException.class,
                () -> userService.completeRegistration(dto));

        verify(userRepository, never()).save(any());
        verify(pendingRegistrationRepository, never()).deleteByEmail(anyString());
    }

    @Test
    @DisplayName("Lista nula de termos é rejeitada como lista vazia")
    void nullAcceptedTermsRejectsRegistration() {
        doThrow(new RequiredTermsNotAcceptedException(
                List.of(DocumentType.TERMS_OF_USE, DocumentType.PRIVACY_POLICY,
                        DocumentType.COOKIES_POLICY)))
                .when(termService).validateRequiredAccepted(any());

        var dto = new CompleteRegistrationDTO(
                "joao@example.com", RAW_PASSWORD, GENERATED_CODE, null);

        assertThrows(RequiredTermsNotAcceptedException.class,
                () -> userService.completeRegistration(dto));
    }

    @Test
    @DisplayName("Corrida de e-mail entre check e insert é convertida em EmailAlreadyInUseException")
    void emailRaceConditionIsConvertedToConflict() {
        when(pendingRegistrationRepository.findByEmail("joao@example.com"))
                .thenReturn(Optional.of(pendingFor("joao@example.com", "STUDENT", null, 0)));
        when(userRepository.existsByEmail("joao@example.com")).thenReturn(false);
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(ENCODED_PASSWORD);
        when(userRepository.save(any(Student.class)))
                .thenThrow(new DataIntegrityViolationException("could not execute statement",
                        new SQLIntegrityConstraintViolationException(
                                "Duplicate entry 'joao@example.com' for key 'users.UK...'",
                                "23000", 1062)));

        assertThrows(EmailAlreadyInUseException.class, () -> userService.completeRegistration(
                new CompleteRegistrationDTO("joao@example.com", RAW_PASSWORD, GENERATED_CODE,
                        ALL_REQUIRED_TERMS)));

        verify(pendingRegistrationRepository, never()).deleteByEmail(anyString());
    }
}
