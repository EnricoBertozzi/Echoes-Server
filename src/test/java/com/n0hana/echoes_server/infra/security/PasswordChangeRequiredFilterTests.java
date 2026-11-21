package com.n0hana.echoes_server.infra.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.n0hana.echoes_server.user.model.User;

import jakarta.servlet.FilterChain;
import tools.jackson.databind.ObjectMapper;

class PasswordChangeRequiredFilterTests {

    private PasswordChangeRequiredFilter filter;
    private User user;

    @BeforeEach
    void setUp() {
        filter = new PasswordChangeRequiredFilter(new ObjectMapper());
        user = mock(User.class);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        return request;
    }

    @Test
    @DisplayName("Bloqueia rota comum com 403 e code PASSWORD_CHANGE_REQUIRED")
    void blocksOrdinaryRoute() throws Exception {
        when(user.isMustChangePassword()).thenReturn(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request("GET", "/animals"), response, chain);

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("PASSWORD_CHANGE_REQUIRED"));
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("Libera a troca de senha, o logout e a leitura de /users/me")
    void allowsPasswordChangeLogoutAndProfileRead() throws Exception {
        when(user.isMustChangePassword()).thenReturn(true);

        for (String[] r : new String[][] { { "PATCH", "/api/v1/auth/password/change" },
                { "POST", "/api/v1/auth/logout" }, { "GET", "/api/v1/users/me" } }) {
            MockHttpServletRequest request = request(r[0], r[1]);
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
            assertEquals(200, response.getStatus());
        }
    }

    @Test
    @DisplayName("Só a leitura de /users/me é liberada: exportar, alterar e excluir continuam bloqueados")
    void otherUsersMeRoutesStayBlocked() throws Exception {
        when(user.isMustChangePassword()).thenReturn(true);

        for (String[] r : new String[][] { { "PATCH", "/api/v1/users/me" }, { "DELETE", "/api/v1/users/me" },
                { "GET", "/api/v1/users/me/export" } }) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request(r[0], r[1]), response, mock(FilterChain.class));
            assertEquals(403, response.getStatus(), r[0] + " " + r[1]);
        }
    }

    @Test
    @DisplayName("Sem a flag, nada é barrado")
    void passesWhenFlagIsOff() throws Exception {
        when(user.isMustChangePassword()).thenReturn(false);
        MockHttpServletRequest request = request("GET", "/animals");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("Requisição sem usuário autenticado segue adiante")
    void passesWhenAnonymous() throws Exception {
        SecurityContextHolder.clearContext();
        MockHttpServletRequest request = request("GET", "/animals");
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        verify(chain).doFilter(any(), any());
    }
}
