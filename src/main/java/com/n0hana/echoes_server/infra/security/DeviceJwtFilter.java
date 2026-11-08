package com.n0hana.echoes_server.infra.security;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.n0hana.echoes_server.device.DeviceJwtService;
import com.n0hana.echoes_server.device.DeviceModel;

import com.nimbusds.jwt.SignedJWT;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DeviceJwtFilter extends OncePerRequestFilter {

    private final DeviceJwtService deviceJwtService;

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

        String issuer;
        try {
            issuer = SignedJWT.parse(token)
                    .getJWTClaimsSet()
                    .getIssuer();
        } catch (Exception exception) {
            // Token malformado: deixa os demais filtros tratarem
            filterChain.doFilter(request, response);
            return;
        }

        // Não processa tokens de usuários
        if (!DeviceJwtService.DEVICE_ISSUER.equals(issuer)) {
            filterChain.doFilter(request, response);
            return;
        }

        DeviceModel device;
        try {
            // Valida assinatura e claims do dispositivo
            device = deviceJwtService.validateToken(token);
        } catch (Exception exception) {
            SecurityContextHolder.clearContext();

            response.sendError(
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Invalid device token");
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
