package com.n0hana.echoes_server.infra.security;

import java.io.IOException;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.n0hana.echoes_server.animal.AnimalJwtService;
import com.n0hana.echoes_server.animal.AnimalModel;

import com.nimbusds.jwt.SignedJWT;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AnimalJwtFilter extends OncePerRequestFilter {

    private final AnimalJwtService animalJwtService;

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
        if (!AnimalJwtService.DEVICE_ISSUER.equals(issuer)) {
            filterChain.doFilter(request, response);
            return;
        }

        AnimalModel animal;
        try {
            // Valida assinatura e claims do dispositivo
            animal = animalJwtService.validateToken(token);
        } catch (Exception exception) {
            SecurityContextHolder.clearContext();

            response.sendError(
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Invalid device token");
            return;
        }

        var authentication =
                new UsernamePasswordAuthenticationToken(
                        animal,
                        null,
                        java.util.List.of(
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
