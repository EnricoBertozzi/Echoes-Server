package com.n0hana.echoes_server.infra.security;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.n0hana.echoes_server.auth.exception.AuthFailedException;
import com.n0hana.echoes_server.auth.session.SessionCookie;
import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.model.User;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Autentica a requisição pelo JWT do header {@code Authorization} ou pelo cookie de
 * sessão (S10).
 *
 * <p>
 * Autenticação só pelo cookie em método que altera estado exige o header
 * {@code X-Echoes-Client}. Um site de outra origem não consegue enviá-lo sem
 * preflight CORS, que só é liberado para origens configuradas (CSRF).
 * </p>
 */
@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        String token = jwtTokenService.recoverToken(header);
        boolean fromCookie = false;

        if (token == null) {
            token = SessionCookie.read(request).orElse(null);
            fromCookie = token != null;
        }

        if (fromCookie && isStateChanging(request) && request.getHeader(SessionCookie.CLIENT_HEADER) == null) {
            token = null;
        }

        final String authToken = token;
        if (authToken != null && !jwtTokenService.isRevoked(authToken)) {
            try {
                UUID userId = UUID.fromString(jwtTokenService.validate(authToken));

                userRepository.findById(userId).ifPresent(user -> {
                    Boolean isValidUser = !user.isEnabled() || !user.isAccountNonLocked() || !user.isAccountNonExpired() || !user.isCredentialsNonExpired();
                    if (isValidUser) {
                        SecurityContextHolder.clearContext();
                        return;
                    }

                    if (issuedBeforeRevocation(authToken, user)) {
                        SecurityContextHolder.clearContext();
                        return;
                    }

                    var auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(auth);
                });

            } catch (AuthFailedException | IllegalArgumentException ex) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    private static boolean isStateChanging(HttpServletRequest request) {
        String method = request.getMethod();
        return !("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)
                || "OPTIONS".equalsIgnoreCase(method));
    }

    /**
     * Um token emitido até o segundo do último evento de revogação (troca/reset de
     * senha ou revogação de consentimento) é considerado revogado (S2). Tokens sem
     * iat só são aceitos se o usuário nunca passou por esses eventos.
     */
    private boolean issuedBeforeRevocation(String token, User user) {
        Instant cutoff = latest(user.getPasswordChangedAt(), user.getTokensRevokedAt());
        if (cutoff == null) {
            return false;
        }

        Instant issuedAt = jwtTokenService.issuedAt(token);
        if (issuedAt == null) {
            return true;
        }

        return !issuedAt.isAfter(cutoff.truncatedTo(ChronoUnit.SECONDS));
    }

    private static Instant latest(Instant a, Instant b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isAfter(b) ? a : b;
    }
}
