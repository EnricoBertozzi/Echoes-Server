package com.n0hana.echoes_server.infra;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import com.n0hana.echoes_server.infra.security.exception.SecurityExceptionHandler;

class SecurityExceptionHandlerTest {

    private final SecurityExceptionHandler handler = new SecurityExceptionHandler();

    @Test
    @DisplayName("AccessDeniedException → 403 com mensagem genérica")
    void accessDeniedReturns403() {
        var response = handler.handleAccessDenied(new AccessDeniedException("detalhe interno"));
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("Acesso negado", response.getBody().get("message"));
    }

    @Test
    @DisplayName("AuthenticationException → 401 com mensagem genérica")
    void authenticationReturns401() {
        var response = handler.handleAuthentication(new BadCredentialsException("detalhe interno"));
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Não autenticado", response.getBody().get("message"));
    }
}
