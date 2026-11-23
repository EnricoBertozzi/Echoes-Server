package com.n0hana.echoes_server.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
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
import com.n0hana.echoes_server.user.dto.PendingInvitationDTO;
import com.n0hana.echoes_server.user.dto.PendingRegistrationDTO;
import com.n0hana.echoes_server.user.dto.UserDTO;
import com.n0hana.echoes_server.user.exception.EmailAlreadyInUseException;
import com.n0hana.echoes_server.user.exception.UserNotFoundException;
import com.n0hana.echoes_server.user.model.Admin;
import com.n0hana.echoes_server.user.model.Manager;
import com.n0hana.echoes_server.user.model.Student;
import com.n0hana.echoes_server.user.model.Teacher;
import com.n0hana.echoes_server.user.model.User;
import com.n0hana.echoes_server.user.model.UserRole;

@ExtendWith(MockitoExtension.class)
class UserRegisterServiceCoverageTests {

    private static final List<DocumentType> TERMS =
        List.of(DocumentType.TERMS_OF_USE, DocumentType.PRIVACY_POLICY, DocumentType.COOKIES_POLICY);

    @Mock
    private NotifierService notifierService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PendingRegistrationRepository pendingRepository;
    @Mock
    private TwoFactorService mfaService;
    @Mock
    private TermService termService;
    @Mock
    private InstitutionService institutionService;

    @InjectMocks
    private UserRegisterService service;

    private static PendingRegistration pending(String email, UserRole role, UUID institutionId) {
        return new PendingRegistration("Fulano", "Silva", email, role.getName(), institutionId, "123456",
            Instant.now().plusSeconds(600), 0, Instant.now().minusSeconds(60));
    }

    private static InstitutionModel institution() {
        InstitutionModel institution = new InstitutionModel();
        institution.setId(UUID.randomUUID());
        return institution;
    }

    private static CompleteRegistrationDTO complete(String email) {
        return new CompleteRegistrationDTO(email, "Senha#Forte1", "123456", TERMS);
    }

    // ---------------------------------------------------------- completeRegistration

    @Test
    @DisplayName("completeRegistration cria cada tipo de usuário e vincula a instituição do convite")
    void completeRegistrationPerRole() {
        InstitutionModel inst = institution();
        when(institutionService.findById(inst.getId())).thenReturn(inst);
        when(passwordEncoder.encode("Senha#Forte1")).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        for (UserRole role : List.of(UserRole.MANAGER, UserRole.TEACHER, UserRole.STUDENT)) {
            String email = role.name().toLowerCase() + "@example.com";
            when(pendingRepository.findByEmail(email)).thenReturn(Optional.of(pending(email, role, inst.getId())));

            UserDTO dto = service.completeRegistration(complete(email));

            assertEquals(role, dto.role());
            assertEquals(inst.getId(), dto.institutionId());
            assertEquals(email, dto.email());
            verify(pendingRepository).deleteByEmail(email);
        }
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository, org.mockito.Mockito.times(3)).save(saved.capture());
        assertInstanceOf(Manager.class, saved.getAllValues().get(0));
        assertInstanceOf(Teacher.class, saved.getAllValues().get(1));
        assertInstanceOf(Student.class, saved.getAllValues().get(2));
        assertTrue(saved.getAllValues().stream().allMatch(u -> "hash".equals(u.getPassword())));
    }

    @Test
    @DisplayName("completeRegistration de administrador não consulta instituição")
    void completeRegistrationAdmin() {
        when(pendingRepository.findByEmail("admin@example.com"))
            .thenReturn(Optional.of(pending("admin@example.com", UserRole.ADMIN, null)));
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserDTO dto = service.completeRegistration(complete("admin@example.com"));

        assertEquals(UserRole.ADMIN, dto.role());
        assertNull(dto.institutionId());
        verify(institutionService, never()).findById(any());
        verify(termService).registerInitialAcceptances(any(Admin.class), org.mockito.ArgumentMatchers.eq(TERMS));
    }

    @Test
    @DisplayName("Violação de integridade que não é de e-mail duplicado é repropagada")
    void nonEmailIntegrityViolationIsRethrown() {
        when(pendingRepository.findByEmail("a@example.com"))
            .thenReturn(Optional.of(pending("a@example.com", UserRole.ADMIN, null)));
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        DataIntegrityViolationException failure = new DataIntegrityViolationException(
            "falha", new SQLIntegrityConstraintViolationException("outra", "23000", 1048));
        when(userRepository.save(any(User.class))).thenThrow(failure);

        DataIntegrityViolationException thrown =
            assertThrows(DataIntegrityViolationException.class, () -> service.completeRegistration(complete("a@example.com")));

        assertSame(failure, thrown);
        verify(pendingRepository, never()).deleteByEmail(anyString());
        verify(termService, never()).registerInitialAcceptances(any(), any());
    }

    @Test
    @DisplayName("Violação de integridade com causa genérica (não SQL) é repropagada")
    void genericCauseIntegrityViolationIsRethrown() {
        when(pendingRepository.findByEmail("a@example.com"))
            .thenReturn(Optional.of(pending("a@example.com", UserRole.ADMIN, null)));
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenThrow(new DataIntegrityViolationException("falha"));

        assertThrows(DataIntegrityViolationException.class, () -> service.completeRegistration(complete("a@example.com")));
    }

    @Test
    @DisplayName("Violação com SQLState 23505 (unique do Postgres) vira EmailAlreadyInUseException")
    void postgresUniqueViolationBecomesConflict() {
        when(pendingRepository.findByEmail("a@example.com"))
            .thenReturn(Optional.of(pending("a@example.com", UserRole.ADMIN, null)));
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenThrow(new DataIntegrityViolationException(
            "dup", new SQLIntegrityConstraintViolationException("dup", "23505", 0)));

        assertThrows(EmailAlreadyInUseException.class, () -> service.completeRegistration(complete("a@example.com")));
    }

    @Test
    @DisplayName("Violação com causa SQLException comum (não de integridade) é repropagada")
    void plainSqlExceptionCauseIsRethrown() {
        when(pendingRepository.findByEmail("a@example.com"))
            .thenReturn(Optional.of(pending("a@example.com", UserRole.ADMIN, null)));
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenThrow(
            new DataIntegrityViolationException("x", new SQLException("x", "23505", 1062)));

        assertThrows(DataIntegrityViolationException.class, () -> service.completeRegistration(complete("a@example.com")));
    }

    @Test
    @DisplayName("Código com tamanho diferente do esperado é recusado e conta tentativa preservando o prazo")
    void wrongLengthCodeCountsAttempt() {
        PendingRegistration p = pending("a@example.com", UserRole.ADMIN, null);
        when(pendingRepository.findByEmail("a@example.com")).thenReturn(Optional.of(p));

        assertThrows(com.n0hana.echoes_server.user.exception.InvalidTwoFactorCodeException.class,
            () -> service.completeRegistration(new CompleteRegistrationDTO("a@example.com", "Senha#Forte1", "12", TERMS)));

        ArgumentCaptor<PendingRegistration> updated = ArgumentCaptor.forClass(PendingRegistration.class);
        verify(pendingRepository).savePreservingTtl(updated.capture());
        assertEquals(1, updated.getValue().attempts());
        assertEquals(p.createdAt(), updated.getValue().createdAt());
        assertEquals(p.expiresAt(), updated.getValue().expiresAt());
        verify(userRepository, never()).save(any());
    }

    // ---------------------------------------------------------- invitations

    @Test
    @DisplayName("assertEmailAvailable lança conflito quando o e-mail já existe")
    void assertEmailAvailable() {
        when(userRepository.existsByEmail("a@example.com")).thenReturn(true);

        assertThrows(EmailAlreadyInUseException.class, () -> service.assertEmailAvailable("a@example.com"));
        service.assertEmailAvailable("b@example.com");
    }

    @Test
    @DisplayName("createAdmin normaliza o e-mail, guarda o convite e dispara a notificação")
    void createAdminInvite() {
        when(mfaService.generateCode()).thenReturn("654321");

        PendingRegistrationDTO dto = service.createAdmin(new CreateUserDTO("Ana", "Silva", "  ANA@Example.COM "));

        assertEquals("ana@example.com", dto.email());
        assertEquals(UserRole.ADMIN, dto.role());
        ArgumentCaptor<PendingRegistration> saved = ArgumentCaptor.forClass(PendingRegistration.class);
        verify(pendingRepository).save(saved.capture());
        assertEquals("654321", saved.getValue().code());
        assertEquals(0, saved.getValue().attempts());
        verify(notifierService).sendRegisterInvite("ana@example.com", "654321", "ana@example.com");
    }

    @Test
    @DisplayName("createManager/Teacher/Student guardam o papel e a instituição no convite")
    void createInstitutionInvites() {
        UUID instId = UUID.randomUUID();
        when(mfaService.generateCode()).thenReturn("111111");
        CreateInstitutionUserDTO dto = new CreateInstitutionUserDTO("Beto", "Souza", "beto@example.com", instId);

        assertEquals(UserRole.MANAGER, service.createManager(dto).role());
        assertEquals(UserRole.TEACHER, service.createTeacher(dto).role());
        PendingRegistrationDTO student = service.createStudent(dto);

        assertEquals(UserRole.STUDENT, student.role());
        assertEquals(instId, student.institutionId());
        ArgumentCaptor<PendingRegistration> saved = ArgumentCaptor.forClass(PendingRegistration.class);
        verify(pendingRepository, org.mockito.Mockito.times(3)).save(saved.capture());
        assertEquals(List.of("MANAGER", "TEACHER", "STUDENT"),
            saved.getAllValues().stream().map(PendingRegistration::role).toList());
        assertTrue(saved.getAllValues().stream().allMatch(p -> instId.equals(p.institutionId())));
    }

    // ---------------------------------------------------------- listPending

    private PendingRegistration invite(String email, UserRole role, UUID institutionId) {
        return pending(email, role, institutionId);
    }

    @Test
    @DisplayName("listPending: solicitante inexistente lança UserNotFoundException")
    void listPendingUnknownRequester() {
        when(userRepository.findByEmail("x@example.com")).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> service.listPending("x@example.com", null));
        verify(pendingRepository, never()).listActive();
    }

    @Test
    @DisplayName("listPending: administrador sem filtro vê todos os convites, sem expor código")
    void listPendingAdminSeesAll() {
        UUID inst = UUID.randomUUID();
        PendingRegistration a = invite("a@example.com", UserRole.STUDENT, inst);
        PendingRegistration b = invite("b@example.com", UserRole.ADMIN, null);
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(new Admin()));
        when(pendingRepository.listActive()).thenReturn(List.of(a, b));

        List<PendingInvitationDTO> result = service.listPending("admin@example.com", null);

        assertEquals(2, result.size());
        assertEquals(new PendingInvitationDTO("Fulano", "Silva", "a@example.com", UserRole.STUDENT, inst,
            a.createdAt(), a.expiresAt()), result.get(0));
        assertEquals(UserRole.ADMIN, result.get(1).role());
        assertNull(result.get(1).institutionId());
    }

    @Test
    @DisplayName("listPending: administrador com filtro vê só os convites da instituição pedida")
    void listPendingAdminFiltered() {
        UUID inst = UUID.randomUUID();
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(new Admin()));
        when(pendingRepository.listActive()).thenReturn(List.of(
            invite("a@example.com", UserRole.STUDENT, inst),
            invite("b@example.com", UserRole.TEACHER, UUID.randomUUID()),
            invite("c@example.com", UserRole.ADMIN, null)));

        List<PendingInvitationDTO> result = service.listPending("admin@example.com", inst);

        assertEquals(1, result.size());
        assertEquals("a@example.com", result.get(0).email());
    }

    @Test
    @DisplayName("listPending: gestor sem instituição recebe lista vazia sem consultar o Redis")
    void listPendingManagerWithoutInstitution() {
        when(userRepository.findByEmail("g@example.com")).thenReturn(Optional.of(new Manager()));

        assertTrue(service.listPending("g@example.com", null).isEmpty());
        verify(pendingRepository, never()).listActive();
    }

    @Test
    @DisplayName("listPending: gestor vê só a própria instituição (com ou sem filtro igual)")
    void listPendingManagerOwnInstitution() {
        InstitutionModel own = institution();
        Manager manager = new Manager();
        manager.setInstitution(own);
        when(userRepository.findByEmail("g@example.com")).thenReturn(Optional.of(manager));
        when(pendingRepository.listActive()).thenReturn(List.of(
            invite("a@example.com", UserRole.STUDENT, own.getId()),
            invite("b@example.com", UserRole.STUDENT, UUID.randomUUID())));

        assertEquals(1, service.listPending("g@example.com", null).size());
        assertEquals(1, service.listPending("g@example.com", own.getId()).size());
    }

    @Test
    @DisplayName("listPending: gestor pedindo outra instituição recebe AccessDeniedException")
    void listPendingManagerOtherInstitution() {
        InstitutionModel own = institution();
        Manager manager = new Manager();
        manager.setInstitution(own);
        when(userRepository.findByEmail("g@example.com")).thenReturn(Optional.of(manager));

        assertThrows(AccessDeniedException.class, () -> service.listPending("g@example.com", UUID.randomUUID()));
        verify(pendingRepository, never()).listActive();
    }
}
