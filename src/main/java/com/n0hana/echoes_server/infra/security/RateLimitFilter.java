package com.n0hana.echoes_server.infra.security;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.n0hana.echoes_server.infra.web.ClientIpResolver;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int PER_MINUTE = 10;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Value("${api.rate-limit.enabled:true}")
    private boolean enabled;

    @Value("${api.security.trust-forwarded-for:false}")
    private boolean trustForwardedFor;

    @Value("${api.security.trusted-proxies:}")
    private String trustedProxies;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled || !"POST".equalsIgnoreCase(request.getMethod()) || scopeOf(request) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String scope = scopeOf(request);
        String key = scope + ":" + ClientIpResolver.resolve(request, trustForwardedFor, trustedProxies);

        Bucket bucket = buckets.computeIfAbsent(key, k -> newBucket());

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
            return;
        }

        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"message\":\"Muitas tentativas. Tente novamente mais tarde\"}");
    }

    private static Bucket newBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(PER_MINUTE)
                        .refillGreedy(PER_MINUTE, Duration.ofMinutes(1))
                        .build())
                .build();
    }

    /** rota sensível → nome do escopo do limite; null = não limitada */
    private static String scopeOf(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null)
            return null;
        if (path.equals("/api/v1/auth/login") || path.equals("/api/v1/auth/mfa"))
            return "auth";
        if (path.startsWith("/api/v1/password/"))
            return "password";
        if (path.equals("/api/v1/classrooms/join"))
            return "join";
        return null;
    }
}
