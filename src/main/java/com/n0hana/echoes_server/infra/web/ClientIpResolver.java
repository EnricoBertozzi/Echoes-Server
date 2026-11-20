package com.n0hana.echoes_server.infra.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolve o IP do cliente para auditoria e limitação de taxa.
 *
 * <p>
 * {@code X-Forwarded-For} só é considerado quando o servidor roda atrás de um
 * proxy confiável ({@code api.security.trust-forwarded-for=true}). Caso contrário
 * o cabeçalho é forjável pelo cliente (S13).
 * </p>
 */
public final class ClientIpResolver {

    private ClientIpResolver() {
    }

    public static String resolve(HttpServletRequest request, boolean trustForwardedFor) {
        if (trustForwardedFor) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank() && !"unknown".equalsIgnoreCase(forwarded)) {
                return forwarded.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
