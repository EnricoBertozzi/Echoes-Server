package com.n0hana.echoes_server.infra.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import com.n0hana.echoes_server.device.DeviceModel;
import com.n0hana.echoes_server.device.exception.DeviceAuthenticationException;
import com.n0hana.echoes_server.device.port.DeviceAccessPolicy;
import com.n0hana.echoes_server.device.port.DeviceCredentialVerifier;
import com.n0hana.echoes_server.term.TermService;
import com.n0hana.echoes_server.term.model.DocumentType;
import com.n0hana.echoes_server.user.model.Admin;
import com.n0hana.echoes_server.user.model.User;

import jakarta.servlet.FilterChain;
import tools.jackson.databind.ObjectMapper;

@DisplayName("Filtros de segurança: ramos complementares")
class SecurityFiltersCoverageTests {

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void login(Object principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    private static MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest r = new MockHttpServletRequest(method, path);
        r.setServletPath(path);
        return r;
    }

    // ---------- TermsAcceptanceFilter ----------

    private User userWithPendingTerms(TermService terms, List<DocumentType> pending) {
        Admin admin = new Admin();
        admin.setId(UUID.randomUUID());
        when(terms.getPendingRequiredTerms(admin.getId())).thenReturn(pending);
        login(admin);
        return admin;
    }

    @Test
    @DisplayName("termos: sem autenticação, segue a cadeia")
    void termosSemAuth() throws Exception {
        TermService terms = mock(TermService.class);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest req = request("GET", "/api/v1/classrooms");

        new TermsAcceptanceFilter(terms, new ObjectMapper()).doFilter(req, new MockHttpServletResponse(), chain);

        verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("termos: principal que não é User (dispositivo) segue sem checar")
    void termosPrincipalNaoUser() throws Exception {
        TermService terms = mock(TermService.class);
        FilterChain chain = mock(FilterChain.class);
        login("dispositivo");

        new TermsAcceptanceFilter(terms, new ObjectMapper()).doFilter(request("GET", "/api/v1/x"),
                new MockHttpServletResponse(), chain);

        verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(terms, never()).getPendingRequiredTerms(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("termos: OPTIONS e rotas liberadas não consultam termos pendentes")
    void termosRotasLiberadas() throws Exception {
        TermService terms = mock(TermService.class);
        Admin admin = new Admin();
        admin.setId(UUID.randomUUID());
        login(admin);
        TermsAcceptanceFilter filter = new TermsAcceptanceFilter(terms, new ObjectMapper());

        List<String[]> casos = List.of(new String[] { "OPTIONS", "/api/v1/x" }, new String[] { "GET", "/terms/x" },
                new String[] { "POST", "/api/v1/auth/login" }, new String[] { "POST", "/api/v1/password/reset" },
                new String[] { "GET", "/api/v1/users/me" }, new String[] { "POST", "/api/v1/users/2fa/setup" },
                new String[] { "POST", "/users/2fa/setup" }, new String[] { "GET", "/swagger-ui/index.html" },
                new String[] { "GET", "/v3/api-docs" }, new String[] { "GET", "/actuator/health" },
                new String[] { "GET", "/error" });
        for (String[] c : casos) {
            FilterChain chain = mock(FilterChain.class);
            MockHttpServletRequest req = request(c[0], c[1]);
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, chain);
            verify(chain).doFilter(req, res);
        }
        verify(terms, never()).getPendingRequiredTerms(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("termos: sem termos pendentes segue a cadeia")
    void termosSemPendencias() throws Exception {
        TermService terms = mock(TermService.class);
        userWithPendingTerms(terms, List.of());
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest req = request("GET", "/api/v1/classrooms");
        MockHttpServletResponse res = new MockHttpServletResponse();

        new TermsAcceptanceFilter(terms, new ObjectMapper()).doFilter(req, res, chain);

        verify(chain).doFilter(req, res);
        assertEquals(200, res.getStatus());
    }

    @Test
    @DisplayName("termos: com pendências responde 403 JSON e não segue a cadeia")
    void termosPendentes() throws Exception {
        TermService terms = mock(TermService.class);
        userWithPendingTerms(terms, List.of(DocumentType.values()[0]));
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse res = new MockHttpServletResponse();

        new TermsAcceptanceFilter(terms, new ObjectMapper()).doFilter(request("GET", "/api/v1/classrooms"), res,
                chain);

        assertEquals(403, res.getStatus());
        assertTrue(res.getContentType().startsWith("application/json"));
        assertTrue(res.getContentAsString().contains(DocumentType.values()[0].name()));
        verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    // ---------- PasswordChangeRequiredFilter ----------

    private Admin mustChange(boolean must) {
        Admin admin = new Admin();
        admin.setId(UUID.randomUUID());
        admin.setMustChangePassword(must);
        login(admin);
        return admin;
    }

    @Test
    @DisplayName("troca de senha: sem autenticação ou sem exigência, segue")
    void trocaDeSenhaSegue() throws Exception {
        PasswordChangeRequiredFilter filter = new PasswordChangeRequiredFilter(new ObjectMapper());
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest req = request("GET", "/api/v1/x");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, chain);
        mustChange(false);
        filter.doFilter(req, res, chain);
        login("dispositivo");
        filter.doFilter(req, res, chain);

        verify(chain, org.mockito.Mockito.times(3)).doFilter(req, res);
    }

    @Test
    @DisplayName("troca de senha: rotas permitidas passam, GET em /users/me passa, POST não")
    void trocaDeSenhaRotas() throws Exception {
        mustChange(true);
        PasswordChangeRequiredFilter filter = new PasswordChangeRequiredFilter(new ObjectMapper());

        for (String[] c : new String[][] { { "OPTIONS", "/api/v1/x" }, { "POST", "/api/v1/auth/login" },
                { "POST", "/api/v1/password/change" }, { "GET", "/api/v1/users/me" }, { "GET", "/actuator/info" },
                { "GET", "/error" } }) {
            FilterChain chain = mock(FilterChain.class);
            MockHttpServletRequest req = request(c[0], c[1]);
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, chain);
            verify(chain).doFilter(req, res);
        }

        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(request("POST", "/api/v1/users/me"), res, chain);
        assertEquals(403, res.getStatus());
        assertTrue(res.getContentAsString().contains(PasswordChangeRequiredFilter.CODE));
        verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    // ---------- DeviceJwtFilter ----------

    @Test
    @DisplayName("dispositivo: sem Bearer ou token que não é de dispositivo segue a cadeia")
    void dispositivoSegue() throws Exception {
        DeviceCredentialVerifier verifier = mock(DeviceCredentialVerifier.class);
        DeviceAccessPolicy policy = mock(DeviceAccessPolicy.class);
        when(verifier.supports("user-token")).thenReturn(false);
        DeviceJwtFilter filter = new DeviceJwtFilter(verifier, policy);
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletRequest semHeader = request("GET", "/x");
        filter.doFilter(semHeader, new MockHttpServletResponse(), chain);
        MockHttpServletRequest basic = request("GET", "/x");
        basic.addHeader("Authorization", "Basic abc");
        filter.doFilter(basic, new MockHttpServletResponse(), chain);
        MockHttpServletRequest usuario = request("GET", "/x");
        usuario.addHeader("Authorization", "Bearer user-token");
        filter.doFilter(usuario, new MockHttpServletResponse(), chain);

        verify(chain, org.mockito.Mockito.times(3)).doFilter(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        verify(verifier, never()).verify(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("dispositivo: credencial inválida responde 401 e limpa o contexto")
    void dispositivoInvalido() throws Exception {
        DeviceCredentialVerifier verifier = mock(DeviceCredentialVerifier.class);
        when(verifier.supports("dev")).thenReturn(true);
        when(verifier.verify("dev")).thenThrow(new DeviceAuthenticationException(new RuntimeException("x")));
        login("previo");
        MockHttpServletRequest req = request("GET", "/x");
        req.addHeader("Authorization", "Bearer dev");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        new DeviceJwtFilter(verifier, mock(DeviceAccessPolicy.class)).doFilter(req, res, chain);

        assertEquals(401, res.getStatus());
        assertEquals(null, SecurityContextHolder.getContext().getAuthentication());
        verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("dispositivo: política que não permite operar responde 403")
    void dispositivoNaoPodeOperar() throws Exception {
        DeviceCredentialVerifier verifier = mock(DeviceCredentialVerifier.class);
        DeviceAccessPolicy policy = mock(DeviceAccessPolicy.class);
        DeviceModel device = mock(DeviceModel.class);
        when(verifier.supports("dev")).thenReturn(true);
        when(verifier.verify("dev")).thenReturn(device);
        when(policy.canOperate(device)).thenReturn(false);
        MockHttpServletRequest req = request("GET", "/x");
        req.addHeader("Authorization", "Bearer dev");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        new DeviceJwtFilter(verifier, policy).doFilter(req, res, chain);

        assertEquals(403, res.getStatus());
        verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("dispositivo autorizado é autenticado com ROLE_DEVICE")
    void dispositivoAutorizado() throws Exception {
        DeviceCredentialVerifier verifier = mock(DeviceCredentialVerifier.class);
        DeviceAccessPolicy policy = mock(DeviceAccessPolicy.class);
        DeviceModel device = mock(DeviceModel.class);
        when(verifier.supports("dev")).thenReturn(true);
        when(verifier.verify("dev")).thenReturn(device);
        when(policy.canOperate(device)).thenReturn(true);
        MockHttpServletRequest req = request("GET", "/x");
        req.addHeader("Authorization", "Bearer dev");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        new DeviceJwtFilter(verifier, policy).doFilter(req, res, chain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertEquals(device, auth.getPrincipal());
        assertEquals("ROLE_DEVICE", auth.getAuthorities().iterator().next().getAuthority());
        verify(chain).doFilter(req, res);
    }

    // ---------- RateLimitFilter ----------

    private RateLimitFilter rateLimit(boolean enabled) {
        RateLimitFilter f = new RateLimitFilter();
        ReflectionTestUtils.setField(f, "enabled", enabled);
        ReflectionTestUtils.setField(f, "trustedProxies", "");
        return f;
    }

    private boolean skips(RateLimitFilter f, String method, String uri) {
        MockHttpServletRequest r = new MockHttpServletRequest(method, uri);
        r.setRequestURI(uri);
        return (boolean) ReflectionTestUtils.invokeMethod(f, "shouldNotFilter", r);
    }

    @Test
    @DisplayName("rate limit: só filtra POST nas rotas sensíveis e quando habilitado")
    void rateLimitEscopo() {
        RateLimitFilter on = rateLimit(true);

        assertFalse(skips(on, "POST", "/api/v1/auth/login"));
        assertFalse(skips(on, "POST", "/api/v1/auth/mfa"));
        assertFalse(skips(on, "POST", "/api/v1/password/forgot"));
        assertFalse(skips(on, "post", "/api/v1/classrooms/join"));
        assertTrue(skips(on, "GET", "/api/v1/auth/login"));
        assertTrue(skips(on, "POST", "/api/v1/outra"));
        assertTrue(skips(rateLimit(false), "POST", "/api/v1/auth/login"));
    }

    @Test
    @DisplayName("rate limit: a 11ª tentativa em um minuto recebe 429 e não segue a cadeia")
    void rateLimitBloqueia() throws Exception {
        RateLimitFilter f = rateLimit(true);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse last = null;
        for (int i = 0; i < 11; i++) {
            MockHttpServletRequest r = new MockHttpServletRequest("POST", "/api/v1/auth/login");
            r.setRequestURI("/api/v1/auth/login");
            r.setRemoteAddr("198.51.100.20");
            last = new MockHttpServletResponse();
            f.doFilter(r, last, chain);
        }

        assertEquals(429, last.getStatus());
        assertTrue(last.getContentAsString().contains("Muitas tentativas"));
        verify(chain, org.mockito.Mockito.times(10)).doFilter(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }
}
