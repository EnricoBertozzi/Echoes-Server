package com.n0hana.echoes_server.infra.security;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.n0hana.echoes_server.device.DeviceModel;
import com.n0hana.echoes_server.device.exception.DeviceAuthenticationException;
import com.n0hana.echoes_server.device.port.DeviceAccessPolicy;
import com.n0hana.echoes_server.device.port.DeviceCredentialVerifier;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Autentica dispositivos. Quem prova a identidade é o
 * {@link DeviceCredentialVerifier}; quem decide se o dispositivo pode operar é
 * o {@link DeviceAccessPolicy}.
 */
@RequiredArgsConstructor
public class DeviceJwtFilter extends OncePerRequestFilter {

    private final DeviceCredentialVerifier credentialVerifier;
    private final DeviceAccessPolicy accessPolicy;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String authorization = request.getHeader("Authorization");

        if (authorization == null ||
                !authorization.startsWith("Bearer ")) {

            filterChain.doFilter(request, response);
            return;
        }

        String token = authorization.substring(7);

        // Credenciais de usuário (ou malformadas) seguem para os demais filtros
        if (!credentialVerifier.supports(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        DeviceModel device;
        try {
            device = credentialVerifier.verify(token);
        } catch (DeviceAuthenticationException exception) {
            SecurityContextHolder.clearContext();

            response.sendError(
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Invalid device token");
            return;
        }

        // Dispositivo pendente, inativo ou de instituição inativa não opera
        if (!accessPolicy.canOperate(device)) {
            SecurityContextHolder.clearContext();

            response.sendError(
                    HttpServletResponse.SC_FORBIDDEN,
                    "Device is not authorized to operate");
            return;
        }

        var authentication =
                new UsernamePasswordAuthenticationToken(
                        device,
                        null,
                        List.of(
                                new SimpleGrantedAuthority(
                                        "ROLE_DEVICE"
                                )
                        )
                );

        SecurityContextHolder
                .getContext()
                .setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }
}
