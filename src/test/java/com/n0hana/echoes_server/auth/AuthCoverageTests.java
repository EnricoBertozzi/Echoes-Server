package com.n0hana.echoes_server.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.n0hana.echoes_server.auth.AuthDTO.ChangePasswordRequest;
import com.n0hana.echoes_server.auth.AuthDTO.LoginRequest;
import com.n0hana.echoes_server.auth.AuthDTO.MfaRequest;
import com.n0hana.echoes_server.auth.AuthDTO.MfaResponse;
import com.n0hana.echoes_server.auth.attempt.AttemptService;
import com.n0hana.echoes_server.auth.device.KnownDeviceService;
import com.n0hana.echoes_server.auth.exception.AuthFailedException;
import com.n0hana.echoes_server.auth.exception.InvalidMfaCodeException;
import com.n0hana.echoes_server.auth.exception.LogoutFailedException;
import com.n0hana.echoes_server.auth.exception.PasswordUnchangedException;
import com.n0hana.echoes_server.auth.exception.UserBlockedException;
import com.n0hana.echoes_server.auth.loginattempt.LoginAttemptService;
import com.n0hana.echoes_server.auth.session.SessionCookie;
import com.n0hana.echoes_server.infra.notifier.NotifierService;
import com.n0hana.echoes_server.infra.redis.CodeType;
import com.n0hana.echoes_server.infra.redis.RedisRepository;
import com.n0hana.echoes_server.infra.redis.RedisRepositoryFactory;
import com.n0hana.echoes_server.infra.security.JwtTokenService;
import com.n0hana.echoes_server.mfa.TwoFactorService;
import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.exception.UserNotFoundException;
import com.n0hana.echoes_server.user.model.User;

@ExtendWith(MockitoExtension.class)
class AuthCoverageTests {

    @Mock
    private NotifierService notifier;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TwoFactorService twoFactorService;
    @Mock
    private JwtTokenService jwtTokenService;
    @Mock
    private AuthRepository authRepository;
    @Mock
    private LoginAttemptService attemptService;
    @Mock
    private AttemptService mfaAttempts;
    @Mock
    private KnownDeviceService knownDevices;

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(notifier, passwordEncoder, userRepository, twoFactorService,
                jwtTokenService, authRepository, attemptService, mfaAttempts, knownDevices);
        ReflectionTestUtils.setField(service, "trustForwardedFor", false);
        ReflectionTestUtils.setField(service, "trustedProxies", "");
    }

    @AfterEach
    void clearContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    private User validUser() {
        User user = mock(User.class);
        when(user.isAccountNonLocked()).thenReturn(true);
        when(user.isEnabled()).thenReturn(true);
        return user;
    }

    @Test
    @DisplayName("Login de e-mail inexistente compara contra hash fictício, gerado uma única vez")
    void loginUnknownEmailUsesCachedDummyHash() {
        when(userRepository.findByEmail("a@x.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("dummy");

        assertThrows(AuthFailedException.class, () -> service.login("a@x.com", "pw"));
        assertThrows(AuthFailedException.class, () -> service.login("a@x.com", "pw"));

        verify(passwordEncoder, org.mockito.Mockito.times(1)).encode(anyString());
        verify(passwordEncoder, org.mockito.Mockito.times(2)).matches("pw", "dummy");
        verify(authRepository, never()).save(anyString(), anyString());
    }

    @Test
    @DisplayName("Login de conta desabilitada (mas não travada) também falha sem revelar o motivo")
    void loginDisabledAccountFails() {
        User user = mock(User.class);
        when(user.isAccountNonLocked()).thenReturn(true);
        when(user.isEnabled()).thenReturn(false);
        when(user.getPassword()).thenReturn("hash");
        when(userRepository.findByEmail("a@x.com")).thenReturn(Optional.of(user));

        assertThrows(AuthFailedException.class, () -> service.login("a@x.com", "pw"));

        verify(passwordEncoder).matches("pw", "hash");
        verify(twoFactorService, never()).generateCode();
    }

    @Test
    @DisplayName("MFA com código nulo falha como código inválido")
    void verifyMfaNullCodeIsInvalid() {
        when(authRepository.findByEmail("a@x.com")).thenReturn(Optional.of("123456"));
        when(mfaAttempts.register(any(), eq("a@x.com"))).thenReturn(false);

        assertThrows(InvalidMfaCodeException.class, () -> service.verifyMfaCode("a@x.com", null));

        verify(authRepository, never()).delete(anyString());
    }

    @Test
    @DisplayName("MFA correto mas usuário removido lança UserNotFoundException")
    void verifyMfaUserNotFound() {
        when(authRepository.findByEmail("a@x.com")).thenReturn(Optional.of("123456"));
        when(userRepository.findByEmail("a@x.com")).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> service.verifyMfaCode("a@x.com", "123456"));
    }

    @Test
    @DisplayName("MFA sem contexto de requisição devolve o JWT sem alertar dispositivo novo")
    void verifyMfaWithoutRequestContext() {
        User user = validUser();
        when(user.getEmail()).thenReturn("a@x.com");
        when(authRepository.findByEmail("a@x.com")).thenReturn(Optional.of("123456"));
        when(userRepository.findByEmail("a@x.com")).thenReturn(Optional.of(user));
        when(jwtTokenService.generate(user)).thenReturn("jwt");

        assertEquals("jwt", service.verifyMfaCode("a@x.com", "123456"));

        verify(knownDevices, never()).registerAndCheckNew(any(), any());
        verify(authRepository).delete("a@x.com");
    }

    @Test
    @DisplayName("MFA em dispositivo novo envia o alerta com IP e user-agent")
    void verifyMfaNewDeviceSendsAlert() {
        User user = validUser();
        UUID id = UUID.randomUUID();
        when(user.getId()).thenReturn(id);
        when(user.getEmail()).thenReturn("a@x.com");
        when(authRepository.findByEmail("a@x.com")).thenReturn(Optional.of("123456"));
        when(userRepository.findByEmail("a@x.com")).thenReturn(Optional.of(user));
        when(jwtTokenService.generate(user)).thenReturn("jwt");
        when(knownDevices.registerAndCheckNew(id, "Agent/1.0")).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "Agent/1.0");
        request.setRemoteAddr("10.1.2.3");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertEquals("jwt", service.verifyMfaCode("a@x.com", "123456"));

        verify(notifier).sendNewDeviceLogin(eq("a@x.com"), eq("10.1.2.3"), eq("Agent/1.0"), any());
    }

    @Test
    @DisplayName("MFA em dispositivo já conhecido não envia alerta")
    void verifyMfaKnownDeviceNoAlert() {
        User user = validUser();
        UUID id = UUID.randomUUID();
        when(user.getId()).thenReturn(id);
        when(user.getEmail()).thenReturn("a@x.com");
        when(authRepository.findByEmail("a@x.com")).thenReturn(Optional.of("123456"));
        when(userRepository.findByEmail("a@x.com")).thenReturn(Optional.of(user));
        when(jwtTokenService.generate(user)).thenReturn("jwt");
        when(knownDevices.registerAndCheckNew(id, null)).thenReturn(false);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));

        service.verifyMfaCode("a@x.com", "123456");

        verify(notifier, never()).sendNewDeviceLogin(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Troca de senha de usuário inexistente lança UserNotFoundException")
    void changePasswordUserNotFound() {
        when(userRepository.findByEmail("a@x.com")).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> service.changePassword("a@x.com", "a", "b"));
    }

    @Test
    @DisplayName("Troca de senha de conta desabilitada lança UserBlockedException")
    void changePasswordDisabledUser() {
        User user = mock(User.class);
        when(user.isAccountNonLocked()).thenReturn(true);
        when(user.isEnabled()).thenReturn(false);
        when(userRepository.findByEmail("a@x.com")).thenReturn(Optional.of(user));

        assertThrows(UserBlockedException.class, () -> service.changePassword("a@x.com", "a", "b"));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("No primeiro acesso, trocar para a mesma senha lança PasswordUnchangedException")
    void changePasswordSameOnFirstAccess() {
        User user = validUser();
        when(user.getPassword()).thenReturn("hash");
        when(user.isMustChangePassword()).thenReturn(true);
        when(userRepository.findByEmail("a@x.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old", "hash")).thenReturn(true);

        assertThrows(PasswordUnchangedException.class, () -> service.changePassword("a@x.com", "old", "old"));

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("No primeiro acesso, senha nova diferente é gravada e a flag é limpa")
    void changePasswordDifferentOnFirstAccess() {
        User user = validUser();
        when(user.getPassword()).thenReturn("hash");
        when(user.isMustChangePassword()).thenReturn(true);
        when(user.getEmail()).thenReturn("a@x.com");
        when(userRepository.findByEmail("a@x.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old", "hash")).thenReturn(true);
        when(passwordEncoder.matches("new", "hash")).thenReturn(false);
        when(passwordEncoder.encode("new")).thenReturn("newhash");

        service.changePassword("a@x.com", "old", "new");

        verify(user).setPassword("newhash");
        verify(user).setMustChangePassword(false);
        verify(user).setPasswordChangedAt(any());
        verify(userRepository).save(user);
        verify(attemptService).resetLoginAttempt("a@x.com");
    }

    @Test
    @DisplayName("Logout sem header (null) lança LogoutFailedException e header válido invalida o token")
    void logoutNullHeader() {
        assertThrows(LogoutFailedException.class, () -> service.logout(null));
        verify(jwtTokenService, never()).invalidate(any());

        service.logout("Bearer abc");
        verify(jwtTokenService).invalidate("Bearer abc");
    }

    // ---- Controller ----

    private AuthController controller(AuthService svc) {
        SessionCookie cookie = new SessionCookie();
        ReflectionTestUtils.setField(cookie, "secure", true);
        ReflectionTestUtils.setField(cookie, "sameSite", "Strict");
        ReflectionTestUtils.setField(cookie, "maxAgeSeconds", 7200L);
        return new AuthController(svc, cookie);
    }

    @Test
    @DisplayName("Controller: login delega ao service e devolve 204")
    void controllerLogin() {
        AuthService svc = mock(AuthService.class);

        ResponseEntity<Void> response = controller(svc).login(new LoginRequest("a@x.com", "pw"));

        assertEquals(204, response.getStatusCode().value());
        verify(svc).login("a@x.com", "pw");
    }

    @Test
    @DisplayName("Controller: MFA devolve 204 com cookie de sessão contendo o token")
    void controllerMfa() {
        AuthService svc = mock(AuthService.class);
        when(svc.verifyMfaCode("a@x.com", "123456")).thenReturn("tok");

        ResponseEntity<Void> response = controller(svc).verifyTwoFactor(new MfaRequest("a@x.com", "123456"));

        assertEquals(204, response.getStatusCode().value());
        String cookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertTrue(cookie.contains("echoes_session=tok"));
        assertTrue(cookie.contains("HttpOnly"));
    }

    @Test
    @DisplayName("Controller: troca de senha devolve 200 e limpa o cookie")
    void controllerChangePassword() {
        AuthService svc = mock(AuthService.class);
        Principal principal = () -> "a@x.com";

        ResponseEntity<Void> response = controller(svc)
                .changePassword(new ChangePasswordRequest("old", "New#Pass123"), principal);

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE).contains("Max-Age=0"));
        verify(svc).changePassword("a@x.com", "old", "New#Pass123");
    }

    @Test
    @DisplayName("Controller: logout usa o header Authorization quando presente")
    void controllerLogoutWithHeader() {
        AuthService svc = mock(AuthService.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer h");

        ResponseEntity<Void> response = controller(svc).logout(request);

        assertEquals(204, response.getStatusCode().value());
        verify(svc).logout("Bearer h");
        assertTrue(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE).contains("Max-Age=0"));
    }

    @Test
    @DisplayName("Controller: logout sem header usa o token do cookie como Bearer")
    void controllerLogoutWithCookie() {
        AuthService svc = mock(AuthService.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new jakarta.servlet.http.Cookie(SessionCookie.NAME, "ck"));

        controller(svc).logout(request);

        verify(svc).logout("Bearer ck");
    }

    @Test
    @DisplayName("Controller: logout sem header nem cookie repassa null ao service")
    void controllerLogoutNothing() {
        AuthService svc = mock(AuthService.class);

        controller(svc).logout(new MockHttpServletRequest());

        verify(svc).logout(null);
    }

    // ---- DTO / Repository ----

    @Test
    @DisplayName("Records de AuthDTO expõem seus componentes")
    void dtoRecords() {
        MfaResponse response = new MfaResponse("t");
        assertEquals("t", response.token());
        assertEquals(new MfaResponse("t"), response);
        assertEquals("123456", new MfaRequest("a@x.com", "123456").code());
        assertEquals("pw", new LoginRequest("a@x.com", "pw").password());
        ChangePasswordRequest change = new ChangePasswordRequest("o", "n");
        assertEquals("o", change.currentPassword());
        assertEquals("n", change.newPassword());
        assertTrue(response.toString().contains("t"));
        assertEquals(new MfaRequest("a", "b").hashCode(), new MfaRequest("a", "b").hashCode());
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("AuthRepository delega save/find/delete ao RedisRepository do namespace MFA")
    void repositoryDelegates() {
        RedisRepositoryFactory factory = mock(RedisRepositoryFactory.class);
        RedisRepository<String> store = mock(RedisRepository.class);
        when(factory.string(CodeType.MFA)).thenReturn(store);
        when(store.find("a@x.com")).thenReturn(Optional.of("123456"));
        AuthRepository repository = new AuthRepository(factory);

        repository.save("a@x.com", "123456");
        Optional<String> found = repository.findByEmail("a@x.com");
        repository.delete("a@x.com");

        assertEquals(Optional.of("123456"), found);
        verify(store).save("a@x.com", "123456");
        verify(store).delete("a@x.com");
    }
}
