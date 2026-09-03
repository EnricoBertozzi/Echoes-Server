package com.n0hana.echoes_server.service.ratelimit;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Order(0)
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {

        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null) {
            ip = ip.split(",")[0].trim();
        } else {
            ip = request.getRemoteAddr();
        }
        String path = request.getRequestURI();

        String key = ip + ":" + request.getMethod() + ":" + path;
        if (!path.startsWith("/api")) {
          filterChain.doFilter(request, response);
          return;
        }

        var bucket = rateLimitService.resolveBucket(key);

        var probe = bucket.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            long waitForRefill = probe.getNanosToWaitForRefill() / 1_000_000_000;

            response.setStatus(429);
            response.setContentType("application/json");
            response.setHeader("Retry-After", String.valueOf(waitForRefill));
            response.getWriter().write("{\"error\": \"Too many requests\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
