package com.n0hana.echoes_server.infra.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;

import tools.jackson.databind.ObjectMapper;
import com.n0hana.echoes_server.term.TermService;
import com.n0hana.echoes_server.term.model.DocumentType;

import jakarta.servlet.FilterChain;

class TermsAcceptanceFilterTests {

    private TermService termService;
    private TermsAcceptanceFilter filter;
    private com.n0hana.echoes_server.user.model.User user;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        termService = mock(TermService.class);
        filter = new TermsAcceptanceFilter(termService, new ObjectMapper());

        user = mock(com.n0hana.echoes_server.user.model.User.class);
        when(user.getId()).thenReturn(userId);
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
    @DisplayName("Bloqueia endpoint comum quando há termos pendentes")
    void blocksRequestWhenTermsArePending() throws Exception {
        when(termService.getPendingRequiredTerms(userId))
            .thenReturn(List.of(DocumentType.TERMS_OF_USE));

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request("GET", "/animals"), response, chain);

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("TERMS_OF_USE"));
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("Permite acesso aos endpoints de termos mesmo com pendências")
    void allowsTermsEndpointsWhenTermsArePending() throws Exception {
        MockHttpServletRequest request = request("GET", "/terms/pending");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(termService);
    }

    @Test
    @DisplayName("Permite endpoint comum quando não há termos pendentes")
    void allowsRequestWhenNoTermsArePending() throws Exception {
        when(termService.getPendingRequiredTerms(userId)).thenReturn(List.of());

        MockHttpServletRequest request = request("GET", "/animals");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("Ignora autenticações que não são de usuário JWT, como @WithMockUser")
    void ignoresNonJwtUserPrincipals() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new User("mock", "password", List.of()), null, List.of()));

        MockHttpServletRequest request = request("GET", "/animals");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(termService);
    }

    @Test
    @DisplayName("Permite requisições OPTIONS de preflight")
    void allowsOptionsRequests() throws Exception {
        MockHttpServletRequest request = request("OPTIONS", "/animals");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(termService);
    }
}
