package com.n0hana.echoes_server.infra.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.n0hana.echoes_server.user.model.User;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@RequiredArgsConstructor
public class PasswordChangeRequiredFilter extends OncePerRequestFilter {

    public static final String CODE = "PASSWORD_CHANGE_REQUIRED";

    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof User user)
                || !user.isMustChangePassword() || isAllowed(request)) {
            chain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), Map.of(
                "message", "É necessário trocar a senha antes de continuar",
                "code", CODE));
    }

    private boolean isAllowed(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // a URI sem o context path; getServletPath() vem vazio quando o servlet é mapeado em "/"
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.startsWith("/api/v1/auth")
                || path.startsWith("/api/v1/password")
                || ("GET".equalsIgnoreCase(request.getMethod()) && path.equals("/api/v1/users/me"))
                || path.startsWith("/actuator")
                || path.equals("/error");
    }
}
