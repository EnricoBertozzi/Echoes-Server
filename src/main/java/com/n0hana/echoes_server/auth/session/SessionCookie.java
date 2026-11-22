package com.n0hana.echoes_server.auth.session;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

@Component
public class SessionCookie {

    public static final String NAME = "echoes_session";

    /** header que o front envia em toda requisição; exigido em mutações feitas só com cookie (CSRF) */
    public static final String CLIENT_HEADER = "X-Echoes-Client";

    @Value("${api.session.cookie-secure:true}")
    private boolean secure;

    @Value("${api.session.same-site:Strict}")
    private String sameSite;

    /** mesma validade do JWT (2 h) */
    @Value("${api.session.max-age-seconds:7200}")
    private long maxAgeSeconds;

    public ResponseCookie issue(String token) {
        return base(token).maxAge(maxAgeSeconds).build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path("/");
    }

    /** token da sessão, se o cookie estiver presente */
    public static Optional<String> read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null)
            return Optional.empty();

        for (Cookie cookie : cookies) {
            if (NAME.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank())
                return Optional.of(cookie.getValue());
        }
        return Optional.empty();
    }
}
