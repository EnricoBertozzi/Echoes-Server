package com.n0hana.echoes_server.infra.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import tools.jackson.databind.ObjectMapper;
import com.n0hana.echoes_server.term.TermService;
import com.n0hana.echoes_server.term.model.DocumentType;
import com.n0hana.echoes_server.user.model.User;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Impede o uso da aplicação por usuários autenticados via JWT enquanto existir
 * algum termo obrigatório publicado que ainda não tenha sido aceito.
 * <p>
 * Os endpoints de termos permanecem liberados para que o usuário consiga ler e
 * registrar o novo aceite. Usuários de dispositivo e autenticações de teste não
 * passam por esta verificação.
 */
@RequiredArgsConstructor
public class TermsAcceptanceFilter extends OncePerRequestFilter {

    private final TermService termService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof User user)
                || !requiresTermsAcceptance(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        List<DocumentType> pending = termService.getPendingRequiredTerms(user.getId());
        if (pending.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        writeTermsAcceptanceRequired(response, pending);
    }

    /**
     * Endpoints que continuam acessíveis mesmo com termos pendentes, pois são
     * necessários para ler/aceitar os termos ou encerrar a sessão.
     */
    private boolean requiresTermsAcceptance(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return false;
        }

        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !(path.startsWith("/terms")
                || path.startsWith("/api/v1/auth")
                || path.startsWith("/api/v1/password")
                || path.startsWith("/api/v1/users/me")
                || path.startsWith("/api/v1/users/2fa")
                || path.startsWith("/users/2fa")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/actuator")
                || path.equals("/error"));
    }

    private void writeTermsAcceptanceRequired(HttpServletResponse response, List<DocumentType> pending)
            throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), Map.of(
                "message", "É necessário aceitar os termos atualizados",
                "pendingTerms", pending));
    }
}
