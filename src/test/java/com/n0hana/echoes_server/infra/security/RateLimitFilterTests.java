package com.n0hana.echoes_server.infra.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

class RateLimitFilterTests {

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter();
        ReflectionTestUtils.setField(filter, "enabled", true);
    }

    private int post(String path, String ip) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRequestURI(path);
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return chain.getRequest() == null ? response.getStatus() : 200;
    }

    @Test
    @DisplayName("Login: 10 tentativas passam e a 11ª do mesmo IP recebe 429")
    void blocksAfterTenAttempts() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertEquals(200, post("/api/v1/auth/login", "10.0.0.1"), "tentativa " + (i + 1));
        }
        assertEquals(429, post("/api/v1/auth/login", "10.0.0.1"));
    }

    @Test
    @DisplayName("O balde é por IP: outro IP não é afetado")
    void bucketIsPerIp() throws Exception {
        for (int i = 0; i < 11; i++) post("/api/v1/auth/mfa", "10.0.0.2");
        assertEquals(200, post("/api/v1/auth/mfa", "10.0.0.3"));
    }

    @Test
    @DisplayName("Login e MFA compartilham o mesmo escopo; senha e entrada em turma têm escopos próprios")
    void scopesAreIndependent() throws Exception {
        for (int i = 0; i < 10; i++) post("/api/v1/auth/login", "10.0.0.4");
        assertEquals(429, post("/api/v1/auth/mfa", "10.0.0.4"));
        assertEquals(200, post("/api/v1/password/forgot", "10.0.0.4"));
        assertEquals(200, post("/api/v1/classrooms/join", "10.0.0.4"));
    }

    @Test
    @DisplayName("Rotas não sensíveis e métodos que não são POST não são limitados")
    void ignoresOtherRoutes() throws Exception {
        for (int i = 0; i < 30; i++) {
            assertEquals(200, post("/api/v1/classrooms", "10.0.0.5"));
        }
        MockHttpServletRequest get = new MockHttpServletRequest("GET", "/api/v1/auth/login");
        get.setRequestURI("/api/v1/auth/login");
        for (int i = 0; i < 30; i++) {
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(get, new MockHttpServletResponse(), chain);
            assertNotNull(chain.getRequest());
        }
    }

    @Test
    @DisplayName("Desligado por configuração, não limita")
    void disabledDoesNotLimit() throws Exception {
        ReflectionTestUtils.setField(filter, "enabled", false);
        for (int i = 0; i < 30; i++) {
            assertEquals(200, post("/api/v1/auth/login", "10.0.0.6"));
        }
    }

    @Test
    @DisplayName("X-Forwarded-For só é considerado quando confiável")
    void forwardedForIgnoredUnlessTrusted() throws Exception {
        for (int i = 0; i < 10; i++) post("/api/v1/auth/login", "10.0.0.7");
        MockHttpServletRequest spoofed = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        spoofed.setRequestURI("/api/v1/auth/login");
        spoofed.setRemoteAddr("10.0.0.7");
        spoofed.addHeader("X-Forwarded-For", "203.0.113.9");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(spoofed, response, new MockFilterChain());
        assertEquals(429, response.getStatus(), "cabeçalho forjado não pode burlar o limite");
        assertNull(response.getErrorMessage());
    }
}
