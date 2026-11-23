package com.n0hana.echoes_server.infra.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.n0hana.echoes_server.auth.exception.AuthFailedException;
import com.n0hana.echoes_server.auth.session.SessionCookie;
import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.model.Admin;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;

@DisplayName("JwtFilter: autenticação por Bearer e por cookie de sessão")
class JwtFilterCoverageTests {

    private JwtTokenService tokens;
    private UserRepository users;
    private JwtFilter filter;
    private FilterChain chain;
    private MockHttpServletResponse response;
    private Admin user;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        tokens = mock(JwtTokenService.class);
        users = mock(UserRepository.class);
        filter = new JwtFilter(tokens, users);
        chain = mock(FilterChain.class);
        response = new MockHttpServletResponse();
        user = new Admin();
        user.setId(userId);
        user.setActive(true);

        when(tokens.recoverToken(null)).thenReturn(null);
        when(tokens.recoverToken("Bearer tok")).thenReturn("tok");
        when(tokens.isRevoked("tok")).thenReturn(false);
        when(tokens.validate("tok")).thenReturn(userId.toString());
        when(tokens.issuedAt("tok")).thenReturn(Instant.now());
        when(users.findById(userId)).thenReturn(Optional.of(user));

        // contexto prévio, para provar que é limpo quando o usuário é rejeitado
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("previo", null));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest bearer() {
        MockHttpServletRequest r = new MockHttpServletRequest("GET", "/api/v1/x");
        r.addHeader("Authorization", "Bearer tok");
        return r;
    }

    private MockHttpServletRequest cookie(String method, boolean clientHeader) {
        MockHttpServletRequest r = new MockHttpServletRequest(method, "/api/v1/x");
        r.setCookies(new Cookie(SessionCookie.NAME, "tok"));
        if (clientHeader) {
            r.addHeader(SessionCookie.CLIENT_HEADER, "web");
        }
        return r;
    }

    private void run(MockHttpServletRequest request) throws Exception {
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("token válido autentica o usuário")
    void autentica() throws Exception {
        run(bearer());

        assertSame(user, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }

    @Test
    @DisplayName("sem token, segue a cadeia sem autenticar")
    void semToken() throws Exception {
        run(new MockHttpServletRequest("GET", "/x"));

        assertEquals("previo", SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        verify(tokens, never()).validate(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("token revogado não autentica")
    void revogado() throws Exception {
        when(tokens.isRevoked("tok")).thenReturn(true);

        run(bearer());

        verify(tokens, never()).validate("tok");
    }

    @Test
    @DisplayName("usuário inexistente não autentica")
    void usuarioInexistente() throws Exception {
        when(users.findById(userId)).thenReturn(Optional.empty());

        run(bearer());

        assertEquals("previo", SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }

    @Test
    @DisplayName("token inválido ou subject que não é UUID limpa o contexto")
    void tokenInvalido() throws Exception {
        when(tokens.validate("tok")).thenThrow(new AuthFailedException());
        run(bearer());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("subject que não é UUID limpa o contexto")
    void subjectInvalido() throws Exception {
        when(tokens.validate("tok")).thenReturn("nao-e-uuid");

        run(bearer());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("usuário inativo limpa o contexto")
    void inativo() throws Exception {
        user.setActive(false);

        run(bearer());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("usuário bloqueado limpa o contexto")
    void bloqueado() throws Exception {
        user.setLockUntil(java.time.LocalDateTime.now().plusHours(1));

        run(bearer());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("token emitido antes da troca de senha é rejeitado")
    void emitidoAntesDaTrocaDeSenha() throws Exception {
        Instant now = Instant.now();
        user.setPasswordChangedAt(now);
        when(tokens.issuedAt("tok")).thenReturn(now.minusSeconds(60));

        run(bearer());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("usa o corte mais recente entre troca de senha e revogação")
    void corteMaisRecente() throws Exception {
        Instant now = Instant.now();
        user.setPasswordChangedAt(now.minusSeconds(3600));
        user.setTokensRevokedAt(now);
        when(tokens.issuedAt("tok")).thenReturn(now.minusSeconds(10));
        run(bearer());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("revogação mais antiga que a troca de senha também vale (cutoff = troca)")
    void corteTrocaMaisRecente() throws Exception {
        Instant now = Instant.now();
        user.setPasswordChangedAt(now);
        user.setTokensRevokedAt(now.minusSeconds(3600));
        when(tokens.issuedAt("tok")).thenReturn(now.minusSeconds(10));

        run(bearer());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("somente revogação definida: token anterior é rejeitado")
    void somenteRevogacao() throws Exception {
        Instant now = Instant.now();
        user.setTokensRevokedAt(now);
        when(tokens.issuedAt("tok")).thenReturn(now.minusSeconds(10));

        run(bearer());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("token sem iat com corte definido é rejeitado")
    void semIat() throws Exception {
        user.setPasswordChangedAt(Instant.now());
        when(tokens.issuedAt("tok")).thenReturn(null);

        run(bearer());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("token emitido depois do corte é aceito")
    void emitidoDepoisDoCorte() throws Exception {
        Instant now = Instant.now();
        user.setPasswordChangedAt(now.minusSeconds(3600));
        when(tokens.issuedAt("tok")).thenReturn(now);

        run(bearer());

        assertSame(user, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }

    @Test
    @DisplayName("cookie em GET autentica sem o header de cliente")
    void cookieGet() throws Exception {
        run(cookie("GET", false));

        assertSame(user, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }

    @Test
    @DisplayName("cookie em HEAD e OPTIONS não exige o header de cliente")
    void cookieHeadOptions() throws Exception {
        run(cookie("HEAD", false));
        assertSame(user, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }

    @Test
    @DisplayName("cookie em mutação sem o header de cliente é ignorado (CSRF)")
    void cookiePostSemHeader() throws Exception {
        run(cookie("POST", false));

        assertEquals("previo", SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        verify(tokens, never()).validate("tok");
    }

    @Test
    @DisplayName("cookie em mutação com o header de cliente autentica")
    void cookiePostComHeader() throws Exception {
        run(cookie("POST", true));

        assertSame(user, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }

    @Test
    @DisplayName("cookie em OPTIONS autentica sem header de cliente")
    void cookieOptions() throws Exception {
        run(cookie("OPTIONS", false));

        assertSame(user, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }
}
