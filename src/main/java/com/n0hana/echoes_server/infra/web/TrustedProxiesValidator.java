package com.n0hana.echoes_server.infra.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Recusa a subida do servidor se {@code TRUSTED_PROXIES} tiver um IP ou CIDR inválido.
 * Sem essa checagem, uma entrada errada seria ignorada em silêncio e o IP da
 * auditoria voltaria a ser o do nginx.
 */
@Component
public class TrustedProxiesValidator {

    private final String trustedProxies;

    public TrustedProxiesValidator(@Value("${api.security.trusted-proxies:}") String trustedProxies) {
        this.trustedProxies = trustedProxies;
    }

    @PostConstruct
    void validate() {
        if (trustedProxies == null || trustedProxies.isBlank()) {
            return;
        }
        for (String entry : trustedProxies.split(",")) {
            String value = entry.trim();
            if (value.isEmpty()) {
                continue;
            }
            try {
                new IpAddressMatcher(value);
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException(
                        "TRUSTED_PROXIES inválido: '" + value + "' (use IP ou CIDR, ex.: 127.0.0.1 ou 172.18.0.0/16)", e);
            }
        }
    }
}
