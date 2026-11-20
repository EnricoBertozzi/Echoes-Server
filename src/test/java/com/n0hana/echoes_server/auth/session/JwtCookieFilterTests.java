package com.n0hana.echoes_server.auth.session;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import com.n0hana.echoes_server.infra.security.JwtFilter;
import com.n0hana.echoes_server.infra.security.JwtTokenService;
import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.model.Teacher;

class JwtCookieFilterTests {

    private final JwtTokenService tokens = mock(JwtTokenService.class);
    private final UserRepository users = mock(UserRepository.class);
    private final JwtFilter filter = new JwtFilter(tokens, users);
    private final UUID id = UUID.randomUUID();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void validToken() {
        Teacher teacher = new Teacher();
        teacher.setId(id);
        when(tokens.recoverToken(any())).thenCallRealMethod();
        when(tokens.validate("tok")).thenReturn(id.toString());
        when(users.findById(id)).thenReturn(Optional.of(teacher));
    }

    private MockHttpServletRequest cookieRequest(String method) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/v1/users/me");
        request.setCookies(new jakarta.servlet.http.Cookie(SessionCookie.NAME, "tok"));
        return request;
    }

    private void run(MockHttpServletRequest request) throws Exception {
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    }

    @Test
    @DisplayName("Cookie HttpOnly autentica leitura (GET) sem header especial (S10)")
    void cookieAuthenticatesReads() throws Exception {
        validToken();
        run(cookieRequest("GET"));
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("Mutação só com cookie, sem X-Echoes-Client, não autentica (CSRF) (S10)")
    void cookieMutationWithoutClientHeaderIsRejected() throws Exception {
        validToken();
        run(cookieRequest("POST"));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("Mutação com cookie e X-Echoes-Client autentica")
    void cookieMutationWithClientHeaderAuthenticates() throws Exception {
        validToken();
        MockHttpServletRequest request = cookieRequest("POST");
        request.addHeader(SessionCookie.CLIENT_HEADER, "web");
        run(request);
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
